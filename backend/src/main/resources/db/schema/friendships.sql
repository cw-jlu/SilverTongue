CREATE TABLE IF NOT EXISTS friendships (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '当前用户 ID',
    friend_id BIGINT NOT NULL COMMENT '好友的用户 ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0: 申请中, 1: 已通过, 2: 已屏蔽',
    remark VARCHAR(64) NULL COMMENT '当前用户给该好友设置的备注名',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关系建立或申请发起时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态最近一次更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_friendships_pair (user_id, friend_id),
    KEY idx_friendships_user_status (user_id, status),
    KEY idx_friendships_friend (friend_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友关系表（双向存储）';
