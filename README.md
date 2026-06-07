# SilverTongue (灵舌) - AI 驱动的全链路英语口语学习生态

[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%203.x-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/Frontend-React%2018-blue)](https://reactjs.org/)
[![Python](https://img.shields.io/badge/AI-Python%203.10-yellow)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

SilverTongue 是一款旨在打通 **“采集即练习，练习即进步”** 闭环的英语口语学习平台。它结合了浏览器插件的便捷采集、影子跟读的科学训练、以及基于端到端大模型（Qwen2.5-Omni）的即时对话反馈。

---

## 📺 演示视频 (Demo Video)

[![SilverTongue Demo](https://img.youtube.com/vi/YOUR_VIDEO_ID/0.jpg)](https://www.youtube.com/watch?v=YOUR_VIDEO_ID)
*点击上方图片查看完整产品演示视频 (待替换为实际链接)*

---

## ✨ 核心特性 (Key Features)

- 🎙️ **Echo 影子跟读**：实时波形对比，支持音素级发音纠错与 MFA (Montreal Forced Aligner) 精准对齐。
- 🤖 **AI 陪练 (The Coach)**：接入 Qwen2.5-Omni 端到端语音模型，实现首字节延迟 <1.2s 的极速全双工语音对练。
- 🌐 **全域采集 (The Harvester)**：一键截取 YouTube/Netflix 语料片段，自动转录、翻译并同步至个人库。
- 🧠 **智能润色 (The Polisher)**：中式英语 (Chinglish) 实时检测，并提供地道表达替换建议。
- 📊 **1000 小时追踪**：可视化热力图追踪“输入”与“输出”时长，科学量化进步。
- 💬 **真人互动大厅**：基于 WebRTC 的真人 1v1 语音对练与主题研讨。

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

## 🚀 快速开始 (Quick Start)

### 1. 环境依赖
- JDK 17+
- Node.js 18+
- Python 3.10+
- Docker & Docker Compose
- NVIDIA GPU (可选，用于本地运行 Omni 模型)

### 2. 部署步骤

#### 克隆仓库
```bash
git clone https://github.com/your-username/SilverTongue.git
cd SilverTongue
```

#### 启动基础服务 (Docker)
```bash
docker-compose up -d mysql redis minio elasticsearch
```

#### 启动后端 (Spring Boot)
1. 配置 `backend/src/main/resources/application.yml` 中的数据库和 MinIO 连接。
2. 运行项目：
```bash
cd backend
./mvnw spring-boot:run
```

#### 启动 AI Agent (Python)
```bash
cd ai-agent
python -m venv .venv
source .venv/bin/activate  # Windows 使用 .venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

#### 启动前端 (React)
```bash
cd frontend
npm install
npm run dev
```

---

## 📜 开源协议 (License)

本项目采用 [MIT License](LICENSE) 开源。

---

## 🤝 贡献与反馈

如果您有任何建议或发现了 Bug，欢迎提交 Issue 或 Pull Request。
- **项目负责人**: [Evann](mailto:evann@example.com)
- **官方文档**: [Docs](./docs)
