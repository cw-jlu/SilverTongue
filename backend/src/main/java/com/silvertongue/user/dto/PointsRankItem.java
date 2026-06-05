package com.silvertongue.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRankItem {

    private int rank;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private long points;
}
