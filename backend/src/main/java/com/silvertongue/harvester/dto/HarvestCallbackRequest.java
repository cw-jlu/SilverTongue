package com.silvertongue.harvester.dto;

import lombok.Data;

/**
 * Python Agent 回调通知 — 下载/切割完成
 */
@Data
public class HarvestCallbackRequest {

    private Long clipId;

    /** 3: 已完成, 4: 失败 */
    private Integer status;

    /** MinIO 中的存储路径 */
    private String storagePath;
}
