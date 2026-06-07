CREATE TABLE IF NOT EXISTS room_participants (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    room_id BIGINT NOT NULL COMMENT '房间 ID',
    user_id BIGINT NOT NULL COMMENT '成员 ID',
    join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '进入时间',
    leave_time DATETIME NULL COMMENT '离开时间',
    role TINYINT NOT NULL DEFAULT 0 COMMENT '0: 成员, 1: 房主',
    ai_role_name VARCHAR(64) NULL COMMENT 'AI角色名称',
    ai_role_setting TEXT NULL COMMENT 'AI角色设定',
    PRIMARY KEY (id),
    KEY idx_room_participant (room_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房间成员记录';
