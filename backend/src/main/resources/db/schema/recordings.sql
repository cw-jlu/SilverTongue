CREATE TABLE IF NOT EXISTS recordings (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    session_id BIGINT NOT NULL COMMENT '关联的会话 ID',
    clip_id BIGINT NULL COMMENT '对应跟读的语料切片 ID (跟读模式下必填)',
    audio_url VARCHAR(512) NOT NULL COMMENT '用户录音文件在 MinIO 中的存储路径',
    score DECIMAL(5,2) NULL COMMENT 'AI 发音评测综合得分 (0.00-100.00)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录音提交及评测时间',
    PRIMARY KEY (id),
    KEY idx_recordings_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户练习录音表';
