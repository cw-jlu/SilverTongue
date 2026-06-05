package com.silvertongue.harvester.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("materials")
public class Material {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String md5;

    private String title;

    /** video, audio, ebook */
    private String type;

    private String sourceUrl;

    private String metadata;

    private String storagePath;

    /** 0: 采集成功, 1: 下载中, 2: 转录中, 3: 解析完成, 4: 失败 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
