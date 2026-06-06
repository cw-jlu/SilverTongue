CREATE TABLE IF NOT EXISTS materials (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    md5 VARCHAR(32) NOT NULL COMMENT '原始音视频文件 MD5 校验码（秒传去重）',
    title VARCHAR(256) NOT NULL COMMENT '素材标题',
    type VARCHAR(16) NOT NULL COMMENT '素材媒介类型: video, audio, ebook',
    source_url VARCHAR(512) NULL COMMENT '原始采集源链接',
    metadata JSON NULL COMMENT '媒体文件详细参数 (时长/分辨率/码率)',
    storage_path VARCHAR(512) NOT NULL COMMENT 'MinIO 内部存储路径',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0: 采集成功, 1: 下载中, 2: 转录中, 3: 解析完成, 4: 失败',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集任务创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '任务状态更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_md5 (md5),
    KEY idx_materials_status (status),
    KEY idx_materials_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始素材元数据表';
