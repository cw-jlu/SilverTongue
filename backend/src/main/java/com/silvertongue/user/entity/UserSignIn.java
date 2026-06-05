package com.silvertongue.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_sign_ins")
public class UserSignIn {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private LocalDate signInDate;

    private Integer pointsRewarded;

    private LocalDateTime createTime;
}
