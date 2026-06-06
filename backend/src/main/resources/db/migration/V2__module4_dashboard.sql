-- ============================================================
-- V2: 模块四 — 数据仪表盘与 SRS 系统 (The Brain)
-- ============================================================

-- 1. practice_sessions 添加时长字段
ALTER TABLE practice_sessions
    ADD COLUMN duration_seconds INT NULL COMMENT '练习时长（秒）' AFTER status;

-- 2. 活动日志表 — 支撑热力图、输入/输出时长统计
CREATE TABLE IF NOT EXISTS activity_log (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    session_id BIGINT NULL COMMENT '关联的 practice_sessions.id',
    activity_type VARCHAR(16) NOT NULL COMMENT 'input (听/读) | output (说/写)',
    source VARCHAR(32) NOT NULL COMMENT 'shadowing | ai_chat | lookup | srs_review | chinglish',
    duration_seconds INT NOT NULL DEFAULT 0 COMMENT '活动时长（秒）',
    description VARCHAR(255) NULL COMMENT '活动描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动时间',
    PRIMARY KEY (id),
    KEY idx_activity_user_time (user_id, create_time),
    KEY idx_activity_type (activity_type),
    KEY idx_activity_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户活动日志（支撑热力图与时长统计）';
