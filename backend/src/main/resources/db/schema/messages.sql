CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    session_id BIGINT NOT NULL COMMENT '关联的会话 ID',
    sender VARCHAR(10) NOT NULL COMMENT 'user 或 ai',
    content TEXT NOT NULL COMMENT '消息文本内容',
    audio_url VARCHAR(512) NULL COMMENT '音频文件在 MinIO 的存储路径',
    refined_content TEXT NULL COMMENT '地道表达推荐',
    chinglish_feedback TEXT NULL COMMENT '中式英语纠错建议',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
    PRIMARY KEY (id),
    KEY idx_messages_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对练消息历史表';
