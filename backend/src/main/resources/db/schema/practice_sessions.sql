CREATE TABLE IF NOT EXISTS practice_sessions (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    type VARCHAR(16) NOT NULL COMMENT '练习类型: shadowing, ai_chat',
    mode VARCHAR(20) NOT NULL COMMENT '交互模式: full_duplex, half_duplex, guided, free_talk',
    topic VARCHAR(255) COMMENT '角色/场景 (例如: 雅思考官, 自由对话)',
    context_file_url VARCHAR(255) COMMENT '场景辅助材料在 MinIO 中的路径',
    user_level VARCHAR(8) NULL COMMENT 'CEFR 等级 (A2, B1, B2, C1)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0: 进行中, 1: 已完成',
    duration_seconds INT NULL COMMENT '练习时长（秒）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话开启时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    report_data TEXT NULL COMMENT '课后总结报告 (JSON string)',
    PRIMARY KEY (id),
    KEY idx_sessions_user (user_id, status),
    KEY idx_sessions_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='练习/会话主表';
