CREATE TABLE IF NOT EXISTS user_sign_ins (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    sign_in_date DATE NOT NULL COMMENT '签到日期',
    points_rewarded INT NOT NULL DEFAULT 0 COMMENT '签到获得的积分奖励',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_signin_date (user_id, sign_in_date),
    KEY idx_signin_user_date (user_id, sign_in_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户签到历史备份表';
