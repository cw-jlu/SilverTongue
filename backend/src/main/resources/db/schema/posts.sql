CREATE TABLE IF NOT EXISTS posts (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '帖子作者 ID',
    content TEXT NOT NULL COMMENT '帖子文本正文',
    clip_id BIGINT NULL COMMENT '关联分享的语料切片 ID',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞总数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    PRIMARY KEY (id),
    KEY idx_posts_user (user_id),
    KEY idx_posts_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区动态/帖子表';
