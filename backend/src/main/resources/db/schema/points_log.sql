CREATE TABLE IF NOT EXISTS points_log (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    change_amount INT NOT NULL COMMENT '积分变动值（正数为增加，负数为扣除）',
    reason VARCHAR(128) NOT NULL COMMENT '积分变动原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '积分变动流水生成时间',
    PRIMARY KEY (id),
    KEY idx_points_log_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分变动明细流水表';
