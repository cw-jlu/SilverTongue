# 🗣️ SilverTongue (灵舌) - AI 驱动的全链路英语口语学习生态

> **采集即练习，练习即进步** — 打通从语料输入到口语输出的完整学习闭环。

---

## 项目简介

SilverTongue 是一款融合了端到端多模态语音 AI 技术的英语口语学习平台。系统通过浏览器插件一键采集 YouTube / Netflix 语料，结合 Qwen2.5-Omni 端到端语音大模型进行 AI 实时对练，并提供音素级发音评测和中式英语检测，帮助用户高效提升英语口语能力。

## 🎬 Demo 视频

> 📌 **TODO**: 上传 Demo 至 B站 / 百度网盘，并在此放入链接。

---

## 技术架构

本项目采用 **gRPC 三服务架构**：

| 服务 | 技术栈 | 端口 | 说明 |
| :--- | :--- | :--- | :--- |
| **Spring Boot 业务服务** | Java 17 / Spring Boot 3 / MyBatis-Plus | 8080 | 用户管理、社区、练习、SRS |
| **Python Agent 智能服务** | Python 3.11 / FastAPI / LangGraph | 50051 (gRPC) | AI 对练、发音评测、RAG |
| **统一监控服务** | Prometheus / Grafana | 9090 / 3001 | 指标采集、可视化面板 |

服务间通过 **gRPC** 高效通信（支持双向音频流传输）。

```
┌─────────────┐    HTTP/WS     ┌──────────────────┐    gRPC     ┌──────────────────┐
│   Frontend   │ ──────────── │  Spring Boot     │ ────────── │  Python Agent    │
│  (React/Vite)│              │  (backend/)      │            │  (ai-agent/)     │
└─────────────┘              └──────────────────┘            └──────────────────┘
                                    │                               │
                               ┌────┴────┐                    ┌────┴────┐
                               │ MySQL   │                    │ Milvus  │
                               │ Redis   │                    │ MFA     │
                               │ MinIO   │                    │ Qwen    │
                               │ ES      │                    └─────────┘
                               └─────────┘
```

---

## 项目结构

```
SilverTongue/
├── docs/               # 设计文档 (PRD, 数据库设计, 开发规范)
├── proto/              # gRPC Protobuf 协议定义 (双端共享)
├── backend/            # Spring Boot 业务服务
├── ai-agent/           # Python Agent 智能服务
├── monitoring/         # Prometheus + Grafana 监控配置
├── frontend/           # React + Vite 前端
└── docker-compose.yml  # 一键启动完整开发环境
```

---

## 快速启动

### 环境要求
- JDK 17+
- Python 3.11+
- Node.js 18+
- Docker & Docker Compose

### 一键启动
```bash
docker compose up -d
```

### 分服务启动
```bash
# 后端
cd backend && mvn spring-boot:run

# AI 服务
cd ai-agent && pip install -r requirements.txt && python main.py

# 前端
cd frontend && npm install && npm run dev
```

---

## 第三方开源组件引用

| 组件 | 版本 | 用途 |
| :--- | :--- | :--- |
| Spring Boot | 3.3.x | Java 后端框架 |
| MyBatis-Plus | 3.5.x | ORM 持久层 |
| FastAPI | 0.115.x | Python Web 框架 |
| LangGraph | 0.3.x | AI Agent 对话状态图 |
| Qwen2.5-Omni | 7B | 端到端多模态语音大模型 |
| MFA (Montreal Forced Aligner) | 3.x | 音素级发音对齐 |
| Milvus | 2.4.x | 向量数据库 (RAG 检索) |
| React | 19.x | 前端 UI 框架 |
| Vite | 6.x | 前端构建工具 |
| Prometheus | 2.x | 指标采集 |
| Grafana | 11.x | 监控可视化 |

---

## 贡献指南

1. 从 `develop` 分支切出功能分支：`feature/A-xxx` 或 `feature/B-xxx`
2. 完成单一功能后提交 PR 至 `develop`
3. PR 描述需包含：标题、功能描述、实现思路、测试方式

---

## License

MIT License
