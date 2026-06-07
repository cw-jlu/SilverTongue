-- ============================================================
-- V5: practice_sessions 添加 report_data 字段
-- 课后总结报告 (JSON string)
-- ============================================================

ALTER TABLE practice_sessions
    ADD COLUMN report_data TEXT NULL COMMENT '课后总结报告 (JSON string)';
