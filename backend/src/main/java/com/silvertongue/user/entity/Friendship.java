package com.silvertongue.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("friendships")
public class Friendship {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long friendId;

    /** 0: 申请中, 1: 已通过, 2: 已屏蔽 */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
