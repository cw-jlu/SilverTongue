# SilverTongue 分模块开发指南与协作规范

> 本文档定义了项目的模块拆分、开发任务清单、验收标准、Git 分支策略及 PR 提交流程。  
> 两位开发者（Dev A / Dev B）必须严格遵守本规范，确保满足评审对"持续交付"与"PR 均匀分布"的要求。

---

## 一、Git 分支策略与工作流

### 1.1 分支结构

```
main                          ← 稳定发布分支，仅通过 develop 的 PR 合入
└── develop                   ← 主集成分支，所有功能 PR 合入此处
     ├── feature/A-xxx        ← Dev A 的功能分支
     └── feature/B-xxx        ← Dev B 的功能分支
```

### 1.2 切出功能分支

```bash
# 1. 确保本地 develop 是最新的
git checkout develop
git pull origin develop

# 2. 从 develop 切出功能分支（命名规范：feature/{开发者}-{模块简称}）
git checkout -b feature/A-user-auth

# 3. 在功能分支上开发，频繁小提交
git add .
git commit -m "feat(user): 实现用户注册接口与密码 BCrypt 加密"

# 4. 推送到远程
git push -u origin feature/A-user-auth
```

### 1.3 提交 PR (Pull Request)

1. 在 GitHub 上访问仓库 → **Pull requests** → **New pull request**
2. **Base 分支**选 `develop`，**Compare 分支**选你的 `feature/A-xxx`
3. 按以下模板填写 PR 描述：

```markdown
## 标题
feat(user): 用户注册、密码登录与 JWT 鉴权

## 功能描述
实现了用户通过用户名+密码注册和登录的完整流程。登录成功后返回 JWT Token，
后续请求通过 Authorization Header 携带 Token 进行身份校验。

## 实现思路
- 密码使用 BCrypt 加密存储
- JWT 使用 JJWT 库签发，有效期 24 小时
- 通过 Spring Security Filter 链进行 Token 拦截和解析
- 用户 ID 采用雪花算法（MyBatis-Plus ASSIGN_ID）

## 测试方式
- 使用 Postman 调用 POST /api/user/register 注册成功，返回 200
- 使用 POST /api/user/login 登录成功，返回 JWT Token
- 携带 Token 访问 GET /api/user/me 返回用户信息
- 不携带 Token 访问受保护接口返回 401
```

4. 指定另一位开发者为 **Reviewer**
5. Review 通过后 **Squash and Merge** 合入 `develop`
6. 合入后删除远程功能分支

### 1.4 PR 节奏要求

> ⚠️ **评审规则要求 PR 分布均匀，严禁最后突击提交。**

- **目标频率**：每人每 2-3 天至少提交 1 个 PR
- **粒度**：每个 PR 只实现 **单一功能**，不混合多个模块
- **Commit 风格**：使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式
  - `feat(模块): 描述` — 新功能
  - `fix(模块): 描述` — 修复
  - `docs: 描述` — 文档更新
  - `refactor(模块): 描述` — 重构

---

## 二、模块拆分与任务清单

### 阶段一：基础设施与核心能力（第 1-2 周）

---

#### 模块 M0：gRPC 协议与代码生成
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A + Dev B 协商 |
| **分支名** | `feature/B-grpc-proto-gen` |
| **所在目录** | `proto/`, `backend/src/.../grpc/`, `ai-agent/proto/` |
| **任务** | 1. 确认 `agent.proto` 和 `assessment.proto` 最终字段定义<br>2. 配置 Maven protobuf-maven-plugin 自动生成 Java Stub<br>3. 使用 `grpcio-tools` 生成 Python Stub<br>4. 双端各写一个 Hello 级别的 gRPC 调用验证连通性 |
| **验收标准** | ✅ `mvn compile` 可自动生成 Java gRPC 桩代码<br>✅ `python -m grpc_tools.protoc` 可生成 Python 桩代码<br>✅ Spring Boot 端可成功调用 Python Agent 的健康检查 gRPC 方法并得到响应 |

---

#### 模块 M1：用户注册与登录
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-user-auth` |
| **所在目录** | `backend/src/.../user/`, `backend/src/.../config/` |
| **任务** | 1. 创建 `users` 表（对应 Database_Design 2.1）<br>2. 实现 POST `/api/user/register` 注册接口（BCrypt 密码加密）<br>3. 实现 POST `/api/user/login` 登录接口（返回 JWT Token）<br>4. 实现 Spring Security JWT Filter 拦截链<br>5. 实现 GET `/api/user/me` 获取当前登录用户信息 |
| **验收标准** | ✅ 注册成功写入 MySQL users 表，密码为 BCrypt 密文<br>✅ 登录返回有效 JWT Token<br>✅ 携带 Token 可访问受保护接口<br>✅ 无 Token 或过期 Token 返回 401 |

---

#### 模块 M2：微信 OAuth 登录
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-wx-login` |
| **所在目录** | `backend/src/.../user/` |
| **任务** | 1. 实现 GET `/api/user/wx/callback` 微信授权回调接口<br>2. 通过 code 换取 access_token 和 openid/unionid<br>3. 若 unionid 已存在则直接登录返回 JWT，否则自动创建新账号<br>4. 已有账号的用户可通过 POST `/api/user/bindWx` 绑定微信 |
| **验收标准** | ✅ 新微信用户授权后自动注册，`wx_openid`/`wx_unionid` 入库<br>✅ 已绑定用户微信扫码直接登录，返回 JWT<br>✅ 同一 unionid 不会创建重复账号（唯一索引保护） |

---

#### 模块 M3：好友系统
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-friendship` |
| **所在目录** | `backend/src/.../user/` |
| **任务** | 1. 创建 `friendships` 表（双向记录方案）<br>2. 实现 POST `/api/friend/apply` 发起好友申请<br>3. 实现 POST `/api/friend/accept` 通过好友申请（写入双向记录）<br>4. 实现 GET `/api/friend/list` 好友列表<br>5. 实现 PUT `/api/friend/remark` 修改备注名 |
| **验收标准** | ✅ 申请通过后 friendships 表有 2 条对向记录<br>✅ 各自可独立设置备注名，互不影响<br>✅ 屏蔽对方后，对方好友列表中该用户消失 |

---

#### 模块 M4：签到与积分
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-signin-points` |
| **所在目录** | `backend/src/.../user/` |
| **任务** | 1. 创建 `user_sign_ins` 和 `points_log` 表<br>2. 实现 POST `/api/signin` 每日签到（Redis Bitmap + MySQL 备份）<br>3. 实现 GET `/api/signin/calendar` 月度签到日历查询<br>4. 实现积分变动流水记录<br>5. 实现 GET `/api/rank/points` 积分排行榜（Redis Sorted Set） |
| **验收标准** | ✅ 同一天重复签到返回"今日已签到"<br>✅ Redis Bitmap `user:signin:{uid}:{yyyyMM}` 对应位被正确设置<br>✅ 积分排行榜 Top N 数据正确 |

---

#### 模块 M5：Python Agent 服务框架
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev B |
| **分支名** | `feature/B-agent-framework` |
| **所在目录** | `ai-agent/` |
| **任务** | 1. 编写 `main.py` FastAPI + gRPC 服务启动入口<br>2. 配置 Prometheus metrics 端点 `/metrics`<br>3. 配置 Redis 连接（DB 3）<br>4. 实现 gRPC Health Check 方法<br>5. 编写基础的 `agent/state.py` LangGraph 状态模型定义 |
| **验收标准** | ✅ `python main.py` 可同时启动 FastAPI (8089) 和 gRPC (50051) 服务<br>✅ 访问 `http://localhost:8089/metrics` 返回 Prometheus 格式指标<br>✅ gRPC 健康检查可从 Spring Boot 端调通 |

---

### 阶段二：核心业务功能（第 3-4 周）

---

#### 模块 M6：素材上传与切片管理
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-material-upload` |
| **所在目录** | `backend/src/.../harvester/` |
| **任务** | 1. 创建 `materials` 和 `clips` 表<br>2. 实现 POST `/api/material/upload` 素材上传（MinIO + MD5 秒传去重）<br>3. 实现 POST `/api/material/{id}/clips` 切片创建<br>4. 实现 GET `/api/clips` 切片列表查询 |
| **验收标准** | ✅ 文件上传至 MinIO `st-materials` 桶，MD5 写入 materials 表<br>✅ 相同 MD5 文件再次上传秒传成功（不重复存储）<br>✅ 切片 CRUD 正常工作 |

---

#### 模块 M7：练习会话与录音
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-practice-session` |
| **所在目录** | `backend/src/.../coach/` |
| **任务** | 1. 创建 `practice_sessions` 和 `recordings` 表<br>2. 实现 POST `/api/session/create` 创建练习会话<br>3. 实现 POST `/api/session/{id}/recording` 提交录音（上传至 MinIO `st-recordings`）<br>4. 通过 gRPC 调用 Python Agent 的 `AssessPronunciation` 获取评分并写入 |
| **验收标准** | ✅ 录音文件存入 MinIO，元数据存入 recordings 表<br>✅ gRPC 调用 Python 评分服务成功，score 字段有值<br>✅ 会话状态流转正常（进行中 → 已完成） |

---

#### 模块 M8：MFA 发音评测服务
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev B |
| **分支名** | `feature/B-mfa-assessment` |
| **所在目录** | `ai-agent/services/` |
| **任务** | 1. 封装 `mfa_aligner.py`（输入音频+文本，输出 TextGrid 音素对齐 JSON）<br>2. 实现 `assessment_service.py` gRPC `AssessPronunciation` 方法<br>3. 计算 accuracy / fluency / completeness 综合评分<br>4. 返回逐词逐音素打分明细 |
| **验收标准** | ✅ 传入一段英语音频和目标文本，返回 0-100 综合评分<br>✅ 每个单词有独立评分，每个音素有起止时间<br>✅ gRPC 接口响应时间 < 5 秒（15 秒以内的音频） |

---

#### 模块 M9：LangGraph 对话引擎
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev B |
| **分支名** | `feature/B-langgraph-agent` |
| **所在目录** | `ai-agent/agent/` |
| **任务** | 1. 实现 `graph.py` 对话状态图（场景选择 → 对话循环 → 结束总结）<br>2. 实现 `nodes.py` 各节点逻辑（场景初始化、用户输入处理、AI 回复生成）<br>3. 对接 LLM API（可先用 OpenAI 兼容接口，后切换 Qwen2.5-Omni）<br>4. 实现会话上下文存储（Redis DB 3） |
| **验收标准** | ✅ 通过 gRPC `StartSession` 可创建新对话并初始化场景<br>✅ 通过 `ChatStream` 可进行多轮文本对话，上下文连贯<br>✅ 对话历史在 Redis 中可查 |

---

#### 模块 M10：Milvus 向量检索（RAG）
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev B |
| **分支名** | `feature/B-milvus-rag` |
| **所在目录** | `ai-agent/services/` |
| **任务** | 1. 实现 `embedding.py`，将语料切片文本向量化并存入 Milvus<br>2. 创建 Milvus Collection `st_clip_vectors`（1536 维）<br>3. 实现基于 user_id 过滤的相似度检索<br>4. 在 LangGraph 对话节点中集成 RAG 检索结果作为上下文 |
| **验收标准** | ✅ 切片文本可向量化并写入 Milvus<br>✅ 检索 Top-K 相关切片，相关性合理<br>✅ 对话中 AI 可引用用户历史学习过的语料片段 |

---

#### 模块 M11：中式英语检测
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev B |
| **分支名** | `feature/B-chinglish-detect` |
| **所在目录** | `ai-agent/services/` |
| **任务** | 1. 实现 `chinglish_detector.py` 规则引擎（常见 Chinglish 模式库）<br>2. 实现 gRPC `DetectChinglish` 方法<br>3. 在 ChatStream 对话流中集成实时检测并通过 `ChinglishAnalysis` 字段返回 |
| **验收标准** | ✅ 输入 "I very like this" 可检出并建议 "I really like this"<br>✅ 严重程度分级合理（low / medium / high）<br>✅ 对话流中 chinglish 字段能实时返回检测结果 |

---

### 阶段三：社区互动与闭环（第 5-6 周）

---

#### 模块 M12：SRS 闪卡系统
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-srs-cards` |
| **所在目录** | `backend/src/.../coach/` |
| **任务** | 1. 创建 `vocabulary_cards` 和 `user_lookups` 表<br>2. 实现 SuperMemo-2 算法（ease_factor / repetitions / review_interval 计算）<br>3. 实现 POST `/api/card/review` 复习反馈接口（传入评分 0-5）<br>4. 实现 GET `/api/card/due` 获取今日待复习卡片列表 |
| **验收标准** | ✅ 新卡片默认 ease_factor=2.50, repetitions=0, interval=0<br>✅ 连续答对后间隔天数递增（1→6→15→...）<br>✅ 答错后 repetitions 归零，间隔回到 1 天<br>✅ `/api/card/due` 只返回 next_review_time ≤ 当前时间的卡片 |

---

#### 模块 M13：社区帖子与搜索
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-community-post` |
| **所在目录** | `backend/src/.../square/` |
| **任务** | 1. 创建 `posts` 和 `comments` 表<br>2. 实现帖子 CRUD + 点赞接口<br>3. 实现二级评论（parent_id 树形结构）<br>4. 帖子内容同步写入 Elasticsearch `st_post_index` 索引<br>5. 实现 GET `/api/post/search?q=xxx` 全文检索 |
| **验收标准** | ✅ 发帖内容同步到 ES<br>✅ 搜索关键词可匹配帖子正文（中英文均可）<br>✅ 评论支持嵌套回复，查询时层级正确 |

---

#### 模块 M14：WebRTC 语音房
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A |
| **分支名** | `feature/A-meeting-room` |
| **所在目录** | `backend/src/.../meeting/` |
| **任务** | 1. 创建 `rooms` 和 `room_participants` 表<br>2. 实现 WebSocket 信令服务（offer/answer/candidate 转发）<br>3. 使用 Redis DB 1 维护房间在线成员状态和心跳<br>4. 实现创建房间、加入房间、离开房间接口 |
| **验收标准** | ✅ 两个浏览器 Tab 可通过 WebSocket 完成 WebRTC 信令交换<br>✅ Redis 中实时反映房间成员列表<br>✅ 断线 3 秒后自动从房间移除 |

---

#### 模块 M15：引导式补全
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev B |
| **分支名** | `feature/B-scaffolding` |
| **所在目录** | `ai-agent/services/` |
| **任务** | 1. 实现 gRPC `GetScaffolding` 方法<br>2. 基于当前对话上下文和用户 CEFR 等级，生成 2-3 个表达补全提示<br>3. 提示难度匹配用户水平（Beginner 给简单句，Advanced 给复杂句型） |
| **验收标准** | ✅ 传入不完整句子和 session_id，返回 2-3 个合理补全<br>✅ 补全内容与当前对话主题相关<br>✅ 不同 user_level 返回不同复杂度的建议 |

---

#### 模块 M16：监控仪表盘完善
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A + Dev B |
| **分支名** | `feature/B-monitoring-dashboard` |
| **所在目录** | `monitoring/` |
| **任务** | 1. 为 gRPC 调用添加耗时 Histogram 指标<br>2. 创建 Grafana Dashboard JSON（QPS / P99 延迟 / 错误率面板）<br>3. 添加 AI 推理 TTFT 延迟专项监控<br>4. 配置告警规则（gRPC 错误率 > 5% 告警） |
| **验收标准** | ✅ Grafana 面板可展示 backend 和 ai-agent 的实时 QPS<br>✅ gRPC 调用延迟 P99 有独立图表<br>✅ Prometheus 可正确抓取两个服务的指标数据 |

---

### 阶段四：打磨与交付（第 7 周）

---

#### 模块 M17：前端 UI 集成
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A / Dev B 分工 |
| **分支名** | `feature/A-frontend-pages` |
| **所在目录** | `frontend/src/` |
| **任务** | 1. 实现登录/注册页面<br>2. 实现影子跟读播放器页面<br>3. 实现 AI 对练对话页面（含波形显示）<br>4. 实现社区广场页面<br>5. 实现个人仪表盘（签到日历、积分、SRS 待复习数） |
| **验收标准** | ✅ 所有页面可正常访问并与后端 API 交互<br>✅ 响应式布局，移动端可用<br>✅ UI 交互流畅无卡顿 |

---

#### 模块 M18：Demo 视频与 README 终稿
| 项目 | 内容 |
| :--- | :--- |
| **负责人** | Dev A + Dev B |
| **分支名** | `feature/A-demo-readme` |
| **所在目录** | `README.md` |
| **任务** | 1. 录制完整的 Demo 视频（覆盖全部核心功能演示）<br>2. 上传至 B站 / 百度网盘<br>3. 更新 README.md 中的 Demo 链接<br>4. 完善第三方组件引用声明 |
| **验收标准** | ✅ Demo 视频有语音讲解，覆盖 5 大模块<br>✅ 视频链接可公开访问<br>✅ README 信息完整且专业 |

---

## 三、开发节奏参考时间表

| 周次 | Dev A (Spring Boot) | Dev B (Python Agent) |
| :--- | :--- | :--- |
| **W1** | M0 协议协商 + M1 用户注册登录 | M0 协议生成 + M5 Agent 服务框架 |
| **W2** | M2 微信登录 + M3 好友系统 | M8 MFA 发音评测 |
| **W3** | M4 签到积分 + M6 素材上传 | M9 LangGraph 对话引擎 |
| **W4** | M7 练习会话与录音 | M10 Milvus RAG + M11 Chinglish 检测 |
| **W5** | M12 SRS 闪卡 + M13 社区帖子 | M15 引导式补全 |
| **W6** | M14 WebRTC 语音房 | M16 监控仪表盘完善 |
| **W7** | M17 前端 UI (共同) | M17 前端 UI (共同) + M18 Demo |

> **目标**：全程产出 **18+ 个 PR**，每人每周至少 1-2 个 PR，PR 记录均匀覆盖全开发周期。

---

## 四、验收检查清单（提交前自查）

每个 PR 合入前，开发者需自查以下项目：

- [ ] 功能代码已完成，无编译/运行错误
- [ ] PR 描述包含四要素（标题、功能描述、实现思路、测试方式）
- [ ] 涉及数据库变更时，`docs/Database_Design.md` 已同步更新
- [ ] 涉及功能变更时，`docs/PRD.md` 已同步更新
- [ ] 新增接口已使用 Postman / curl 手动验证
- [ ] Commit message 符合 Conventional Commits 规范
- [ ] 不包含敏感信息（API Key、密码等 hardcode）
