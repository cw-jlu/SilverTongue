ALTER TABLE room_participants ADD COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT '0: 成员, 1: 房主';
ALTER TABLE room_participants ADD COLUMN ai_role_name VARCHAR(64) NULL COMMENT 'AI角色名称';
ALTER TABLE room_participants ADD COLUMN ai_role_setting TEXT NULL COMMENT 'AI角色设定';
