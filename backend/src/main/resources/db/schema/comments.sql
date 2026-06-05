CREATE TABLE IF NOT EXISTS comments (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    post_id BIGINT NOT NULL COMMENT '关联的帖子 ID',
    user_id BIGINT NOT NULL COMMENT '评论者 ID',
    parent_id BIGINT NULL COMMENT '父评论 ID (NULL=一级评论)',
    content TEXT NOT NULL COMMENT '评论文本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (id),
    KEY idx_comments_post (post_id),
    KEY idx_comments_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子评论表';
