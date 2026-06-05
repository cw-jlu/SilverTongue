CREATE TABLE IF NOT EXISTS user_lookups (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    word VARCHAR(128) NOT NULL COMMENT '查询的英文单词/词组',
    clip_id BIGINT NULL COMMENT '发生查询时的视频切片 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '查词时间',
    PRIMARY KEY (id),
    KEY idx_user_lookup (user_id, word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户查词历史表';
