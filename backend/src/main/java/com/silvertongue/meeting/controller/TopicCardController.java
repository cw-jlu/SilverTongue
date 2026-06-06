package com.silvertongue.meeting.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.meeting.dto.TopicCardVO;
import com.silvertongue.meeting.service.TopicCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class TopicCardController {

    private final TopicCardService topicCardService;

    /** 获取房间的话题卡 & 生词辅助卡（首次自动初始化） */
    @GetMapping("/{roomId}/cards")
    public ApiResult<List<TopicCardVO>> getCards(@PathVariable Long roomId) {
        return ApiResult.success(topicCardService.initCardsForRoom(roomId));
    }

    /** 换一批话题卡 */
    @PostMapping("/{roomId}/cards/next")
    public ApiResult<List<TopicCardVO>> nextCards(@PathVariable Long roomId) {
        return ApiResult.success(topicCardService.nextCards(roomId));
    }
}
