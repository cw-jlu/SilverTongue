CREATE TABLE IF NOT EXISTS topic_cards (
    id BIGINT NOT NULL COMMENT 'Snowflake ID',
    room_id BIGINT NOT NULL COMMENT '所属房间',
    type VARCHAR(16) NOT NULL COMMENT 'TOPIC / VOCABULARY',
    content VARCHAR(512) NOT NULL COMMENT '话题内容或单词',
    translation VARCHAR(256) NULL COMMENT '中文翻译/释义',
    display_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_room_type (room_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='语音房间话题卡 & 生词辅助卡';
