package com.silvertongue.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignInResponse {

    /** 本次签到获得的积分 */
    private int pointsRewarded;

    /** 累计签到次数 */
    private int totalSignInCount;

    /** 签到后总积分 */
    private long totalPoints;
}
