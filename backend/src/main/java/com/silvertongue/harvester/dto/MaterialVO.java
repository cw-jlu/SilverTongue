package com.silvertongue.harvester.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialVO {

    private Long id;
    private String md5;
    private String title;
    private String type;
    private String sourceUrl;
    private String metadata;
    private String storagePath;
    private Integer status;
    private LocalDateTime createTime;
}
