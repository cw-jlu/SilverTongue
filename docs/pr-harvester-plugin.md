## 标题
feat(harvester): 实现 FR-1.1 浏览器插件与一键下载 Pipeline

## 功能描述
基于 [everyone-can-use-english](https://github.com/xiaolai/everyone-can-use-english) 项目的 enjoy-app 架构参考，为 SilverTongue 实现了 PRD 中 FR-1.1 "万能浏览器插件与一键下载器" 的完整链路：

1. **Chrome 浏览器扩展** — 在 YouTube/Netflix 播放页面自动注入浮动"采集"按钮，用户可选取起止时间，一键提交采集请求
2. **后端 Harvester REST API** — `POST /api/clips/harvest` 接收采集请求，创建 Material + Clip 记录，异步触发下载 Pipeline
3. **Python yt-dlp + FFmpeg Pipeline** — 封装完整的下载→切割→上传流程，支持 MinIO 存储
4. **状态轮询 + 回调机制** — 前端轮询 `GET /api/clips/status/{id}`，Python 回调 `POST /api/clips/callback`

## 实现思路

### 浏览器扩展 (`frontend/extension/`)
- Manifest V3，content_script 注入 YouTube/Netflix 页面
- 检测页面中 `<video>` 元素的 `currentTime`，提供起止时间选择面板
- 通过 `chrome.storage.local` 存储 JWT Token，请求时自动附带 Authorization Header
- 采集成功后自动轮询后端状态，实时展示下载/切割进度

### 后端 API (`backend/.../harvester/`)
- `HarvestClipRequest` DTO — 接收 `{ url, platform, startTime, endTime }`
- `HarvestCallbackRequest` DTO — Python 回调 `{ clipId, status, storagePath }`
- `ClipService.harvest()` — 创建 Material (MD5 秒传去重) + Clip (status=0 待处理)，异步触发下载
- `ClipService.updateStatus()` — 接收回调，更新 Clip 和 Material 状态
- SecurityConfig 放行 `/api/clips/callback`（Python 脚本无 JWT）

### Python Pipeline (`ai-agent/services/harvester.py`)
- 从 enjoy 项目的 `youtubedr.ts` / `ffmpeg.ts` 参考实现，改造为 Spring Boot 可调用的独立脚本
- 使用 yt-dlp（替代 enjoy 的 youtubedr Go 二进制）下载视频
- FFmpeg 切割视频 + 提取音频（供影子跟读使用）
- MinIO 上传视频/音频文件，完成后回调 Spring Boot

### 数据流
```
YouTube 页面 → 🎙️采集按钮 → POST /api/clips/harvest
  → Spring Boot 创建 Material + Clip
  → 异步线程调 Python harvester.py
    → yt-dlp 下载 → ffmpeg 切割 → MinIO 上传
    → POST /api/clips/callback 通知完成
  → 前端轮询 /api/clips/status/{id}
```

## 附带修复
| 修复 | 文件 |
|:---|:---|
| MapperScan 范围过窄（只扫了 user.mapper） | `SilverTongueApplication.java` — 改为 `**.mapper` |
| ElasticsearchRestTemplate 已移除 | `PostService.java` — 替换为 `ElasticsearchOperations` |
| pom.xml 缺少 protobuf-maven-plugin | 新增 gRPC 桩代码自动生成 |
| ES 端口与本地 cpolar 冲突 | docker-compose + application.yml 改为 9201 |
| langchain-core 版本冲突 | `requirements.txt` — 0.3.28 → 0.3.29 |

## 测试方式
1. 安装扩展：Chrome → `chrome://extensions/` → 加载 `frontend/extension/`
2. 确保 yt-dlp 和 ffmpeg 已安装（`pip install yt-dlp`，ffmpeg 桌面已有）
3. 启动后端：`java -jar silvertongue-backend-1.0.0-SNAPSHOT.jar`
4. 打开 YouTube 任意视频 → 点击右下角 🎙️采集 → 选起止时间 → 提交
5. 观察后端日志确认 Pipeline 执行
