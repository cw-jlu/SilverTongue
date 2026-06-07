package com.silvertongue.meeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.meeting.dto.TopicCardVO;
import com.silvertongue.meeting.entity.TopicCard;
import com.silvertongue.meeting.mapper.TopicCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicCardService {

    private final TopicCardMapper topicCardMapper;

    private static final Random RANDOM = new Random();

    // ==================== 预置话题库 ====================

    private static final String[][] TOPIC_POOL = {
        {"What's your favorite hobby and why?", "你最喜欢的爱好是什么？为什么？"},
        {"Describe a place you'd love to visit.", "描述一个你特别想去的地方。"},
        {"What was the last movie you watched?", "你最近看的一部电影是什么？"},
        {"If you could have dinner with any historical figure, who would it be?", "如果你能和一位历史人物共进晚餐，你会选谁？"},
        {"What's the most interesting book you've read?", "你读过最有趣的一本书是什么？"},
        {"How do you usually spend your weekends?", "你通常怎么过周末？"},
        {"What skill would you like to learn next?", "你下一个想学什么技能？"},
        {"Describe your ideal vacation.", "描述一下你理想的假期。"},
        {"What's your favorite type of music?", "你最喜欢什么类型的音乐？"},
        {"If you could live in any city, where would it be?", "如果你能住在任何城市，你会选哪里？"},
        {"What are you most proud of?", "你最引以为豪的事情是什么？"},
        {"What's a food you could eat every day?", "哪种食物你天天吃都不会腻？"},
        {"Do you prefer working alone or in a team?", "你更喜欢独立工作还是团队合作？"},
        {"What's the best advice you've ever received?", "你收到过最好的建议是什么？"},
        {"Describe a memorable travel experience.", "描述一次难忘的旅行经历。"},
    };

    private static final String[][] VOCABULARY_POOL = {
        {"lifelong learning", "终身学习"},
        {"strike up a conversation", "搭讪、开始交谈"},
        {"see eye to eye", "看法一致"},
        {"break the ice", "打破僵局"},
        {"think outside the box", "跳出框架思考"},
        {"on the same page", "达成共识"},
        {"burn the midnight oil", "挑灯夜读"},
        {"once in a blue moon", "千载难逢"},
        {"go the extra mile", "付出额外努力"},
        {"piece of cake", "小菜一碟"},
        {"hit the nail on the head", "说到点子上"},
        {"bend over backwards", "竭尽全力"},
        {"keep an open mind", "保持开放心态"},
        {"take it for granted", "视为理所当然"},
        {"actions speak louder than words", "行动胜于言辞"},
    };

    // ==================== 业务方法 ====================

    /**
     * 为房间初始化话题卡和生词卡（首次调用时填充）
     */
    @Transactional
    public List<TopicCardVO> initCardsForRoom(Long roomId) {
        long existing = topicCardMapper.selectCount(new LambdaQueryWrapper<TopicCard>()
                .eq(TopicCard::getRoomId, roomId));
        if (existing > 0) {
            return listCards(roomId);
        }

        // 随机选 3 个话题 + 5 个生词
        List<TopicCard> cards = new ArrayList<>();
        int now = 0;

        int[] topicIndices = RANDOM.ints(0, TOPIC_POOL.length).distinct().limit(3).toArray();
        for (int idx : topicIndices) {
            TopicCard card = new TopicCard();
            card.setRoomId(roomId);
            card.setType("TOPIC");
            card.setContent(TOPIC_POOL[idx][0]);
            card.setTranslation(TOPIC_POOL[idx][1]);
            card.setDisplayOrder(now++);
            card.setCreateTime(LocalDateTime.now());
            cards.add(card);
        }

        int[] vocabIndices = RANDOM.ints(0, VOCABULARY_POOL.length).distinct().limit(5).toArray();
        for (int idx : vocabIndices) {
            TopicCard card = new TopicCard();
            card.setRoomId(roomId);
            card.setType("VOCABULARY");
            card.setContent(VOCABULARY_POOL[idx][0]);
            card.setTranslation(VOCABULARY_POOL[idx][1]);
            card.setDisplayOrder(now++);
            card.setCreateTime(LocalDateTime.now());
            cards.add(card);
        }

        for (TopicCard card : cards) {
            topicCardMapper.insert(card);
        }

        log.info("Init {} topic cards for roomId={}", cards.size(), roomId);
        return toVOList(cards);
    }

    /**
     * 获取房间的全部话题卡/生词卡
     */
    public List<TopicCardVO> listCards(Long roomId) {
        List<TopicCard> cards = topicCardMapper.selectList(new LambdaQueryWrapper<TopicCard>()
                .eq(TopicCard::getRoomId, roomId)
                .orderByAsc(TopicCard::getDisplayOrder));
        return toVOList(cards);
    }

    /**
     * 换一批话题卡
     */
    @Transactional
    public List<TopicCardVO> nextCards(Long roomId) {
        topicCardMapper.delete(new LambdaQueryWrapper<TopicCard>()
                .eq(TopicCard::getRoomId, roomId));
        return initCardsForRoom(roomId);
    }

    private List<TopicCardVO> toVOList(List<TopicCard> cards) {
        return cards.stream()
                .map(c -> TopicCardVO.builder()
                        .id(c.getId())
                        .type(c.getType())
                        .content(c.getContent())
                        .translation(c.getTranslation())
                        .build())
                .collect(Collectors.toList());
    }
}
