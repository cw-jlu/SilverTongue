# SilverTongue (灵舌) - 数据库与多层存储设计规范 v2.6

本文档定义了 SilverTongue 项目的完整存储方案，包含 MySQL 关系型数据库、Milvus 向量数据库、MinIO 对象存储以及 Elasticsearch 全文检索系统的详细 Schema 设计，并提供了服务间 gRPC 通信的 Protobuf 契约设计。

---

## 1. 总体存储架构 (Storage Strategy)

为了支撑全链路口语学习中的高并发、流式交互、向量检索和全文搜索需求，系统采用混合存储方案：

*   **MySQL 8.0**：持久化存储核心业务元数据（用户、社交关系、积分体系、练习记录及系统配置）。
*   **MinIO**：**全量媒体对象存储**（原始视频/音频素材、用户跟读与对话原声音频、TTS 或语音大模型生成的 AI 语音文件、头像等静态资源及字幕对齐 JSON）。
*   **Milvus**：**向量数据库**，用于存储语料切片（Clips）的 Embedding 向量，支持个性化语料库的相似度检索（RAG）。
*   **Elasticsearch**：**全文检索引擎**，用于社区帖子搜索、素材标题搜索以及长篇对话历史的全文检索。
*   **Redis**：提供高频实时状态及排行榜存储（如 WebRTC 语音房间状态、Session 状态缓存、签到位图和排行榜 ZSet）。

---

## 2. MySQL 核心表结构定义 (Business Schema)

### 2.1 用户、社交与激励模块 (User & Gamification)

#### `users` (用户基础信息表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 (雪花算法 Snowflake ID) |
| `username` | `VARCHAR(64)` | 登录用户名 (唯一值，若仅使用微信注册则可为空，后续绑定后填入) |
| `password` | `VARCHAR(128)` | BCrypt 加密密码 (微信直接注册用户可为空) |
| `nickname` | `VARCHAR(64)` | 用户昵称 |
| `avatar_url` | `VARCHAR(256)` | 用户头像在 MinIO 中的链接地址 (首选微信头像，可自定义修改) |
| `points` | `BIGINT` | 用户当前拥有的总积分 |
| `level` | `VARCHAR(20)` | 用户英语水平等级 (`beginner`, `elementary`, `intermediate`, `upper_intermediate`, `advanced`, `proficient`) |
| `sign_in_count` | `INT` | 累计签到次数 |
| `wx_openid` | `VARCHAR(64)` | 微信公众号或小程序针对本应用的 OpenID (独立环境唯一标识) |
| `wx_unionid` | `VARCHAR(64)` | 微信开放平台下唯一的 UnionID (跨公众号/小程序/网页的全局唯一用户标识) |
| `status` | `TINYINT` | 账户状态 (`0`: 正常, `1`: 禁用, `2`: 已注销) |
| `disabled_time` | `DATETIME` | 账户被禁用的具体时间 |
| `deleted_time` | `DATETIME` | 账户执行注销的具体时间 |
| `create_time` | `DATETIME` | 注册时间 |

#### `friendships` (好友关系表)
> [!NOTE]
> 系统采用**双向存储记录**方案：当双方成为好友时，写入两条对立记录（A -> B 和 B -> A）。每个用户拥有自己独立的备注名和屏蔽状态，查询好友只需执行简单的 `WHERE user_id = ?`，免去复杂的 `OR` 查询。

| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 当前用户 ID |
| `friend_id` | `BIGINT` | 好友的用户 ID |
| `status` | `TINYINT` | 关系状态 (`0`: 申请中, `1`: 已通过为好友, `2`: 已屏蔽对方) |
| `remark` | `VARCHAR(64)` | 当前用户给该好友设置的备注名 |
| `create_time` | `DATETIME` | 关系建立或申请发起时间 |
| `update_time` | `DATETIME` | 状态最近一次更新时间 |

#### `points_log` (积分变动明细流水表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `change_amount` | `INT` | 积分变动值 (例如增加：`+10`，兑换扣除：`-50`) |
| `reason` | `VARCHAR(128)` | 积分变动原因描述 (例如 "影子跟读满分奖励") |
| `create_time` | `DATETIME` | 积分变动流水生成时间 |


#### `medals` (勋章定义配置表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `INT` | 主键 |
| `name` | `VARCHAR(64)` | 勋章名称 |
| `icon_url` | `VARCHAR(256)` | 勋章图标在 MinIO 中的静态链接地址 |
| `requirement_json` | `JSON` | 勋章达成条件逻辑 (例如：`{"target": "shadowing_count", "val": 100}`) |

#### `user_medals` (用户已获得勋章关联表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `medal_id` | `INT` | 关联的勋章 ID |
| `obtain_time` | `DATETIME` | 获得该勋章的时间 |

#### `user_sign_ins` (用户签到历史备份表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `sign_in_date` | `DATE` | 签到日期 |
| `points_rewarded` | `INT` | 签到获得的积分奖励 |

---

### 2.2 语料采集与内容模块 (The Harvester)

#### `materials` (原始素材元数据表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `md5` | `VARCHAR(32)` | 原始音视频文件 MD5 校验码 (建立唯一索引，用于快速秒传去重) |
| `title` | `VARCHAR(256)` | 素材标题 (电影名称、原著书籍名称等) |
| `type` | `ENUM` | 素材媒介类型 (`video`, `audio`, `ebook`) |
| `source_url` | `VARCHAR(512)` | 原始采集源链接 (YouTube/Netflix 网页地址) |
| `metadata` | `JSON` | 媒体文件详细参数 (时长、分辨率、码率等) |
| `storage_path` | `VARCHAR(512)` | MinIO 内部的存储绝对路径 |
| `local_path` | `VARCHAR(512)` | 服务端/本地视频下载缓存路径 |
| `status` | `TINYINT` | 下载与转录状态 (`0`: 采集成功, `1`: 下载中, `2`: 转录中, `3`: 解析对齐完成, `4`: 失败) |
| `create_time` | `DATETIME` | 采集任务创建时间 |
| `update_time` | `DATETIME` | 任务状态最近一次更新时间 |


#### `clips` (语料切片表 - 口语影子跟读的最小训练单位)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `material_id` | `BIGINT` | 关联的原始素材 ID |
| `start_time` | `DECIMAL(12,3)` | 该切片在素材中的开始时间 (秒，支持毫秒精度) |
| `end_time` | `DECIMAL(12,3)` | 该切片在素材中的结束时间 (秒，支持毫秒精度) |
| `content` | `TEXT` | 英文对白/字幕原文 |
| `translation` | `TEXT` | 中文字幕翻译 |
| `vector_id` | `VARCHAR(64)` | 对应的 Milvus 向量主键 ID |

#### `notes` (学习笔记表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `clip_id` | `BIGINT` | 关联的语料切片 ID |
| `content` | `TEXT` | 学习笔记的具体文本内容 |
| `create_time` | `DATETIME` | 创建笔记的时间 |

#### `user_lookups` (用户查词历史表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `word` | `VARCHAR(128)` | 查询的英文单词/词组 |
| `clip_id` | `BIGINT` | 发生查询时的视频切片 ID (可为 NULL) |
| `create_time` | `DATETIME` | 查词时间 |

---

### 2.3 练习、评估与 AI 配置 (The Coach)

#### `practice_sessions` (练习/会话主表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `type` | `ENUM` | 练习类型 (`shadowing` 影子跟读, `ai_chat` AI 智能对话) |
| `mode` | `VARCHAR(20)` | 交互模式 (`full_duplex` 全双工语音, `half_duplex` 半双工, `guided` 引导式, `free_talk` 自由对话) |
| `topic` | `VARCHAR(255)` | 会话主题/角色设定 (例如: `雅思考官`, `外企 HR 面试`) |
| `context_file_url` | `VARCHAR(255)` | 场景辅助材料在 MinIO 中的路径 |
| `status` | `TINYINT` | 会话状态 (`0`: 进行中/活跃, `1`: 已完成/归档) |
| `create_time` | `DATETIME` | 会话开启时间 |
| `update_time` | `DATETIME` | 会话状态或进度最近一次更新时间 |


#### `recordings` (用户练习录音表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `session_id` | `BIGINT` | 关联的会话 ID |
| `clip_id` | `BIGINT` | 对应跟读的语料切片 ID (跟读模式下必填) |
| `audio_url` | `VARCHAR(256)` | 用户练习录音文件在 MinIO 中的存储路径 |
| `score` | `DECIMAL(5,2)` | AI 发音评测综合得分 (0.00 - 100.00) |
| `create_time` | `DATETIME` | 录音提交及评测时间 |


#### `assessment_details` (音素级发音深度评估表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `recording_id` | `BIGINT` | 关联的用户录音记录 ID |
| `word` | `VARCHAR(64)` | 评测目标单词 |
| `phonemes_data` | `JSON` | 该单词下各个音素的分数及时间对齐详情 (MFA 算法产出的 JSON 数据) |
| `accuracy` | `FLOAT` | 单词发音准确度分数 |
| `fluency` | `FLOAT` | 单词发音流利度分数 |

#### `ai_chat_metadata` (对话消息元数据表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `session_id` | `BIGINT` | 关联的对话会话 ID |
| `role` | `VARCHAR(20)` | 消息发送角色 (`user` / `assistant`) |
| `audio_url` | `VARCHAR(256)` | 该条回复的语音文件在 MinIO 中的路径 (用户录音或 AI 生成的 TTS 音频) |
| `duration` | `INT` | 语音时长 (秒) |
| `emotion_state` | `VARCHAR(32)` | 用户交互时的情感状态（如 `anxious`, `confident`, `neutral` 等） |
| `chinglish_analysis` | `JSON` | 中式英语及语法纠错分析结果（包括检测出的错误短语、严重程度、修改建议等 JSON 结构） |
| `refined_text` | `TEXT` | 针对用户原话由 AI 生成的地道润色（Native Rewrite）替代文本 |
| `speech_rate` | `INT` | 发音语速 (WPM - Words Per Minute) |
| `create_time` | `DATETIME` | 消息发送时间 |

#### `ai_model_configs` (AI 路由与多模型管理表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `INT` | 主键 |
| `model_name` | `VARCHAR(64)` | 模型唯一标识名称 (如 `qwen2.5-omni`, `deepseek-chat`) |
| `provider` | `VARCHAR(32)` | 模型服务商名称 (如 `OpenAI`, `Ollama`, `DeepSeek`) |
| `capability` | `VARCHAR(20)` | 模型能力类型 (`text_only`: 纯文本, `voice_input`: 接受音频输入, `voice_full`: 音频输入输出) |
| `api_key` | `VARCHAR(256)` | API 访问密钥 (加密存储) |
| `endpoint` | `VARCHAR(256)` | 模型接口调用地址 |
| `priority` | `INT` | 路由优先级 (数值越小优先级越高，默认 100) |
| `max_tokens` | `INT` | 模型最大输出 token 数 (默认 1024) |
| `is_active` | `TINYINT` | 启用状态 (`0`: 禁用, `1`: 启用) |

---

### 2.4 社区与实时互动 (The Square & Meeting)

#### `posts` (社区动态/帖子表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 帖子作者的真实用户 ID |
| `content` | `TEXT` | 帖子文本正文 |
| `clip_id` | `BIGINT` | 关联分享的语料切片 ID (支持一键点击练习) |
| `like_count` | `INT` | 帖子点赞总数 |
| `create_time` | `DATETIME` | 帖子的发布时间 |

#### `comments` (帖子评论表)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `post_id` | `BIGINT` | 关联的帖子 ID |
| `user_id` | `BIGINT` | 评论者的用户 ID |
| `parent_id` | `BIGINT` | 父评论 ID (支持多层树形评论结构，若为一级评论则为 NULL) |
| `content` | `TEXT` | 评论文本正文 |
| `create_time` | `DATETIME` | 评论发表及创建时间 |


#### `rooms` (Meeting 语音房)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `creator_id` | `BIGINT` | 房主 ID |
| `room_name` | `VARCHAR(128)` | 房间名 |
| `max_users` | `INT` | 容纳人数 |
| `status` | `TINYINT` | `0`:活跃, `1`:关闭 |

#### `room_participants` (房间成员记录)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `room_id` | `BIGINT` | 房间 ID |
| `user_id` | `BIGINT` | 成员 ID |
| `join_time` | `DATETIME` | 进入时间 |
| `leave_time` | `DATETIME` | 离开时间 |

---

### 2.5 记忆系统 (The Brain)

#### `vocabulary_cards` (生词闪卡表 - 基于 SRS 算法)
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `word` | `VARCHAR(128)` | 生词拼写 |
| `phonetic_us` | `VARCHAR(128)` | 美式音标 |
| `dictionary_source` | `TEXT` | 来源词典释义文本 (Cambridge 或 MDict 离线解析的释义内容) |
| `phrase` | `TEXT` | 关联的学习上下文短句/例句 |
| `context_clip_id` | `BIGINT` | 来源语料 ID (用于在复习时回看/回听上下文视频切片) |
| `next_review_time` | `DATETIME` | 智能算法推荐的下一次复习时间 |
| `ease_factor` | `DECIMAL(5,2)` | 记忆易度因子 (由 SuperMemo-2 算法动态演进更新，默认值为 2.50) |
| `repetitions` | `INT` | 连续复习正确次数 (由 SuperMemo-2 算法统计，连续答对次数，默认值为 0) |
| `review_interval` | `INT` | 当前复习间隔天数 (由 SuperMemo-2 算法计算，默认值为 0) |
| `create_time` | `DATETIME` | 单词闪卡创建时间 |
| `update_time` | `DATETIME` | 闪卡最近一次复习或状态修改时间 |


---

## 3. Redis 数据库划分与核心 Key 设计 (Redis Allocation)

系统利用 Redis 存储实时高频业务状态及大屏排行榜信息，按数据库索引划分如下：

| DB 索引 | 对应服务 | 核心 Key / 描述用途 |
| :--- | :--- | :--- |
| **DB 0** | `SpringBoot` 业务服务 | 用户 Session Token、注册验证码、全局配置热缓存。 |
| **DB 1** | `SpringBoot` 业务服务 | `meeting:room:{room_id}`: Live Meeting 房间内参与者状态及 WebRTC 信令控制心跳检测。 |
| **DB 2** | `SpringBoot` 业务服务 | **1. 每日签到位图 (Bitmap)**：`user:signin:{user_id}:{yyyyMM}`。<br>**2. 排行榜 (Sorted Set)**：`rank:points` (用户积分 ZSet)，`rank:practice:duration` (口语练习时长 ZSet)。 |
| **DB 3** | `PythonAgent` 智能服务 | `agent:session:history:{session_id}`: LangGraph 执行过程中短期历史多轮对话上下文缓存与话权转换（Turn-taking）控制状态。 |

---

## 4. Milvus 向量空间设计 (Vector DB Schema)

在语料库 RAG 检索中，用于存储语料切片（Clips）的 Embedding 向量：

### `st_clip_vectors` (语料切片语义向量集合)
*   **Collection 参数**：
    *   `id`: `INT64` (主键，支持 AutoID)
    *   `clip_id`: `INT64` (映射关联 MySQL 表中的 `clips.id`)
    *   `vector`: `FLOAT_VECTOR(1536)` (支持 1536 维的语义 Embedding 向量)
    *   `user_id`: `INT64` (标量字段，用于在过滤检索时实现按用户隔离其个人专属的私有语料库)

---

## 5. MinIO 存储桶结构划分 (Object Storage)

| 存储桶 (Bucket) 名称 | 包含内容 | 说明与权限级别 |
| :--- | :--- | :--- |
| `st-materials` | 原始视频、音频素材文件 | **Private**。仅对下载解析服务可见，防盗连。 |
| `st-recordings` | 用户在影子跟读、对话时的录音音频 | **Private**。属于用户私有隐私音频文件。 |
| `st-ai-voices` | 由语音大模型或 TTS 生成的 AI 回复音频 | **Private**。按需通过后端生成有时效性的签名 URL。 |
| `st-transcripts` | MFA 对齐及 Whisper 转录的词级 JSON 文件 | **Private**。播放器后台的对准元数据。 |
| `st-assets` | 用户头像、勋章图标、UI 静态资源 | **Public-Read**。公开免签访问。 |

---

## 6. Elasticsearch 索引设计 (Search Indexing)

### 6.1 `st_post_index` (社区帖子全文搜索索引)
*   **核心字段 mappings**：
    *   `content`: `text` (应用 IK 中文分词)
    *   `nickname`: `keyword` (作者昵称)
    *   `create_time`: `date`

### 6.2 `st_material_index` (原始素材库搜索索引)
*   **核心字段 mappings**：
    *   `title`: `text` (素材标题，应用分词)
    *   `description`: `text` (简介信息)
    *   `type`: `keyword`

### 6.3 `st_conversation_index` (对话历史及语法纠错全文检索索引)
*   **核心字段 mappings**：
    *   `msg_id`: `keyword`
    *   `session_id`: `keyword`
    *   `user_id`: `keyword`
    *   `content`: `text` (用户转录原文/AI 回复原文，应用中文及英文分词)
    *   `refined_content`: `text` (AI 对用户原话进行的 Native Rewrite 地道润色文本)
    *   `create_time`: `date`

---

## 7. gRPC 服务通信契约设计 (gRPC Protobuf Contracts)

Spring Boot 业务服务与 Python Agent 智能服务通过 gRPC 进行高性能流式通信，定义如下两套核心服务合约：

### 7.1 `AgentService.proto` (智能体交互服务)

```protobuf
syntax = "proto3";

package silvertongue.agent;

option java_multiple_files = true;
option java_package = "com.silvertongue.grpc.agent";

service AgentService {
  // 开启 AI 对练会话
  rpc StartSession(StartSessionRequest) returns (StartSessionResponse);
  
  // 语音对话流 (双向流传输音频及状态)
  rpc ChatStream(stream ChatStreamRequest) returns (stream ChatStreamResponse);
  
  // 卡壳时的引导式表达补全
  rpc GetScaffolding(ScaffoldingRequest) returns (ScaffoldingResponse);
}

message StartSessionRequest {
  string user_id = 1;
  string session_id = 2;
  string mode = 3;         // full_duplex, half_duplex, guided, free_talk
  string user_level = 4;   // CEFR 等级
  string topic = 5;        // 练习场景主题 (如面试、点餐)
}

message StartSessionResponse {
  bool success = 1;
  string error_message = 2;
}

message ChatStreamRequest {
  string session_id = 1;
  bytes audio_chunk = 2;       // 输入音频 PCM 原始字节流
  bool is_final_chunk = 3;    // 是否为本次说话的最后一包
  float energy_level = 4;      // 实时音频能量，辅助话权判定
  float silence_duration = 5;  // 持续静默时长
}

message ChatStreamResponse {
  string text_delta = 1;         // 助手回复文本增量 (Stream 文字流)
  bytes audio_chunk = 2;         // 助手回复音频 PCM 字节流
  bool is_finished = 3;          // 本轮播放是否已全部结束
  ChinglishAnalysis chinglish = 4; // 实时中式英语纠错分析
  string refined_text = 5;       // 地道表达推荐
}

message ChinglishAnalysis {
  bool has_chinglish = 1;
  string original_pattern = 2;
  string suggestion = 3;
  string severity = 4; // low, medium, high
}

message ScaffoldingRequest {
  string session_id = 1;
  string incomplete_text = 2;
}

message ScaffoldingResponse {
  repeated string completion_hints = 1; // 2-3 个符合上下文及用户等级的补全提示
}
```

### 7.2 `AssessmentService.proto` (发音评估与纠错分析服务)

```protobuf
syntax = "proto3";

package silvertongue.assessment;

option java_multiple_files = true;
option java_package = "com.silvertongue.grpc.assessment";

service AssessmentService {
  // 发音评估 (MFA 与云端双引擎评估)
  rpc AssessPronunciation(AssessRequest) returns (AssessResponse);
  
  // 单纯的中式英语与语法检测
  rpc DetectChinglish(ChinglishRequest) returns (ChinglishResponse);
}

message AssessRequest {
  string user_id = 1;
  bytes audio_data = 2;       // 跟读音频文件
  string target_text = 3;     // 跟读目标文本
}

message AssessResponse {
  float final_score = 1;          // 综合得分 (0.00-100.00)
  float accuracy = 2;             // 准确度
  float fluency = 3;              // 流利度
  float completeness = 4;         // 完整度
  repeated WordAssessment words = 5; // 单词及音素级时间轴与得分明细
}

message WordAssessment {
  string word = 1;
  float score = 2;
  repeated PhonemeDetail phonemes = 3;
}

message PhonemeDetail {
  string phoneme = 1;
  float score = 2;
  double start_time = 3; // 音素起止时间 (秒)
  double end_time = 4;
}

message ChinglishRequest {
  string text = 1;
}

message ChinglishResponse {
  bool has_chinglish = 1;
  repeated ErrorPattern patterns = 2;
}

message ErrorPattern {
  string category = 1;    // 错误分类
  string error_text = 2;  // 匹配的文本
  string suggestion = 3;  // 修改意见
}
```

---

## 8. MySQL 核心索引与调优建议 (Database Tuning)

*   **`uk_material_md5`**：在 `materials` 表的 `md5` 字段建立**唯一索引**。确保同视频文件在全站仅存在一条物理路径记录，支撑极速上传与去重逻辑。
*   **`idx_friendship_pair`**：在 `friendships` 表上建立联合索引 `(user_id, friend_id)`。优化好友状态列表加载与互友校验的耗时。
*   **`idx_msg_session`**：在 `ai_chat_metadata` 表上建立二级单列索引 `(session_id)`。因为 AI 对练是基于单会话流式交互，拉取历史记录是极高频的行为。
*   **`idx_srs_queue`**：在 `vocabulary_cards` 表上建立联合索引 `(user_id, next_review_time)`。优化 SRS 复习系统拉取生词列表的效率，防止产生全表扫描。
*   **`idx_user_lookup`**：在 `user_lookups` 表上建立联合索引 `(user_id, word)`。便于快速检索某个用户是否曾经查询过某特定生词。
*   **`uk_wx_unionid`**：在 `users` 表的 `wx_unionid` 字段上建立**唯一索引**。用于支持微信扫码/快捷登录与账号绑定的幂等性处理，避免重复注册。
*   **`idx_signin_date`**：在 `user_sign_ins` 表上建立联合索引 `(user_id, sign_in_date)`。用于查询用户的签到明细流水。

