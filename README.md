# SilverTongue) - AI 驱动的全链路英语口语学习平台

[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%203.x-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/Frontend-React%2018-blue)](https://reactjs.org/)
[![Python](https://img.shields.io/badge/AI-Python%203.10-yellow)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

SilverTongue 是一款旨在打通 **“采集即练习，练习即进步”** 闭环的英语口语学习平台。它结合了浏览器插件的便捷采集、影子跟读的科学训练、以及基于端到端大模型的即时对话反馈。

---

## 📺 演示视频 (Demo Video)

[点击查看 Bilibili 产品演示视频](https://b23.tv/PDOjK5y)

---

## ✨ 核心特性 (Key Features)

- 🎙️ **Echo 影子跟读**：实时波形对比，支持音素级发音纠错与 MFA (Montreal Forced Aligner)和外部SDK支持的多维评分。
- 🤖 **AI 陪练 (The Coach)**：接入端到端语音模型，实现全双工语音对练,支持自定义Agent可解析上传资料做个性化对话演练。
- 🌐 **全域采集 (The Harvester)**：一键截取 YouTube/Netflix 语料片段，自动转录、翻译并同步至个人库。(部分完成)
- 🧠 **智能润色 (The Polisher)**：中式英语 (Chinglish) 实时检测，并提供地道表达替换建议。
- 📊 **1000 小时追踪**：可视化热力图追踪“输入”与“输出”时长，科学量化进步。
- 💬 **真人互动大厅**：基于 WebRTC 的真人 多v多 语音对练与主题研讨后期还将加入多真人多Agent的混合对话教学。

---

## 🚀 创新点 (Innovations)

1.  **端到端语音直连 (Speech-to-Speech)**：不止考虑传统的级联管道（STT->LLM->TTS），还有基于 Qwen2.5-Omni 实现音频流到音频流的推理，极大降低了交互延迟并保留了语调情感。
2.  **全链路学习闭环**：首创从浏览器语料一键采集到 AI 对练的无缝衔接，让“你喜欢的电影”瞬间变成“你的练习素材”。
3.  **音素级精准评估**：融合本地 MFA 声学模型与云端评估引擎(Azure SDK)，提供比传统工具更细腻的发音反馈。
4.  **智能显存自适应**：具备针对大模型 GPU 显存的智能调度与避让机制，保障多任务环境下推理节点的稳定性。

---

## 🛠️ 技术难点 (Technical Challenges)

- **跨语言高性能通信**：通过 gRPC 协议实现 Java (Spring Boot) 后端与 Python (AI 推理节点) 之间的高效、低延迟双向数据流传输。
- **全双工语音交互控制**：基于 VAD (语音活动检测) 与话权决策算法，实现自然打断 (Voice Break-in) 与智能停顿判断。
- **低延迟音频流处理**：在 WebRTC 与 WebSocket 环境下处理实时音频流的采集、分片、重采样与无损播放。

---

## 🏗️ 技术架构 (Architecture)

### 后端 (Backend) - Java Spring Boot
- **核心框架**: Spring Boot 3.x, Spring Security (JWT)
- **数据库**: MySQL 8.0, MyBatis Plus
- **存储**: MinIO (对象存储), Redis (缓存/分布式锁)
- **通信**: gRPC (与 AI Agent 通信), WebSocket (实时消息/信令)
- **搜索**: Elasticsearch (UGC 内容检索)

### 前端 (Frontend) - React
- **核心框架**: React 18, Vite
- **状态管理**: Hooks / Context API
- **音频处理**: Wavesurfer.js (波形展示), Web Audio API
- **实时通信**: WebRTC (真人通话)

### AI Agent - Python
- **模型推理**: LangGraph (对话流控), Qwen2.5-Omni (端到端语音大模型)
- **音频分析**: Montreal Forced Aligner (MFA), Azure Speech SDK
- **Web 框架**: FastAPI / gRPC Server

---

## 🔑 必须配置的密钥 (Required Keys)

在使用本项目前，请在 `.env` 或 `application.yml` 中配置以下密钥：

| 模块 | 关键配置项 | 说明 |
| :--- | :--- | :--- |
| **基础服务** | `MYSQL_PASSWORD` / `MINIO_SECRET_KEY` | 数据库与对象存储凭证 |
| **认证** | `JWT_SECRET` | JWT 签名密钥（建议 32 位以上） |
| **大模型** | `QWEN_OMNI_API_KEY` | 阿里云 DashScope Qwen2.5-Omni API 密钥 |
| **翻译/语义** | `LLM_API_KEY` | OpenAI 或兼容接口密钥（用于文本分析） |
| **微信登录** | `WECHAT_APP_ID` / `APP_SECRET` | 微信开放平台应用凭证 |
| **发音评估** | `AZURE_SPEECH_KEY` / `REGION` | 微软 Azure 语音服务密钥（用于高级评估） |

---

## 📅 待完成事项 (Pending Tasks)

- [ ] **Live Meeting 真人互动**：完善多人实时语音房间与话题卡片匹配机制。
- [ ] **SRS 记忆算法闭环**：将 AI 对练中的错句自动同步至艾宾浩斯复习卡片。
- [ ] **移动端适配**：开发基于 React Native 或 PWA 的移动学习端。
- [ ] **UGC 排行榜**：基于 Redis ZSet 实现的语料分享热度与学习积分排行榜。

---

## 🚀 快速开始 (Quick Start)

### 1. 环境依赖
- JDK 17+
- Node.js 18+
- Python 3.10+
- Docker & Docker Compose
- NVIDIA GPU (可选，用于本地运行 Omni 模型)

---

## 📜 开源协议 (License)

本项目采用 [MIT License](LICENSE) 开源。

---

## 🤝 贡献与反馈

如果您有任何建议或发现了 Bug，欢迎提交 Issue 或 Pull Request。
- **项目负责人**: Evann
- **联系邮箱**: [3036896574@qq.com](mailto:3036896574@qq.com)
- **官方文档**: [Docs](./docs)
