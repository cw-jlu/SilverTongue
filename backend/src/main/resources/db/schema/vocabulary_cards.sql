CREATE TABLE IF NOT EXISTS vocabulary_cards (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    word VARCHAR(128) NOT NULL COMMENT '生词拼写',
    phonetic_us VARCHAR(128) NULL COMMENT '美式音标',
    dictionary_source TEXT NULL COMMENT '词典释义 (Cambridge/MDict)',
    phrase TEXT NULL COMMENT '关联上下文例句',
    context_clip_id BIGINT NULL COMMENT '来源语料 ID',
    next_review_time DATETIME NOT NULL COMMENT '下次复习时间',
    ease_factor DECIMAL(5,2) NOT NULL DEFAULT 2.50 COMMENT '记忆易度因子',
    repetitions INT NOT NULL DEFAULT 0 COMMENT '连续复习正确次数',
    review_interval INT NOT NULL DEFAULT 0 COMMENT '当前复习间隔天数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    PRIMARY KEY (id),
    KEY idx_srs_queue (user_id, next_review_time),
    KEY idx_srs_word (user_id, word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生词闪卡表 (SuperMemo-2)';
