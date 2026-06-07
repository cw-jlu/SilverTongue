-- ============================================================
-- V3: practice_sessions 添加 user_level 字段
-- 前端 Chat 页面选择 CEFR 等级后需要持久化存储
-- ============================================================

ALTER TABLE practice_sessions
    ADD COLUMN user_level VARCHAR(8) NULL COMMENT 'CEFR 等级 (A2, B1, B2, C1)' AFTER context_file_url;
