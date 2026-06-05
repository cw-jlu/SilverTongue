CREATE TABLE IF NOT EXISTS clips (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    material_id BIGINT NOT NULL COMMENT '关联的原始素材 ID',
    start_time DECIMAL(12,3) NOT NULL COMMENT '切片开始时间 (秒)',
    end_time DECIMAL(12,3) NOT NULL COMMENT '切片结束时间 (秒)',
    content TEXT NULL COMMENT '英文字幕原文',
    translation TEXT NULL COMMENT '中文字幕翻译',
    vector_id VARCHAR(64) NULL COMMENT '对应的 Milvus 向量主键 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '切片创建时间',
    PRIMARY KEY (id),
    KEY idx_clips_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='语料切片表';
