package com.silvertongue.coach.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.coach.entity.UserLookup;
import com.silvertongue.coach.entity.VocabularyCard;
import com.silvertongue.coach.mapper.UserLookupMapper;
import com.silvertongue.coach.mapper.VocabularyCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LookupService {

    private static final int AUTO_CARD_THRESHOLD = 3; // 查词 ≥3 次自动创建卡片

    private final UserLookupMapper lookupMapper;
    private final VocabularyCardMapper cardMapper;

    /**
     * 记录用户查词。同一单词查询 ≥ AUTO_CARD_THRESHOLD 次时自动创建 SRS 卡片。
     */
    @Transactional
    public void recordLookup(Long userId, String word, Long clipId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 写入查词历史
        UserLookup lookup = new UserLookup();
        lookup.setUserId(userId);
        lookup.setWord(word);
        lookup.setClipId(clipId);
        lookup.setCreateTime(now);
        lookupMapper.insert(lookup);

        // 2. 统计该单词的查词次数
        long count = lookupMapper.selectCount(
                new LambdaQueryWrapper<UserLookup>()
                        .eq(UserLookup::getUserId, userId)
                        .eq(UserLookup::getWord, word)
        );

        // 3. 检查是否已有卡片
        long cardExists = cardMapper.selectCount(
                new LambdaQueryWrapper<VocabularyCard>()
                        .eq(VocabularyCard::getUserId, userId)
                        .eq(VocabularyCard::getWord, word)
        );

        // 4. 达到阈值且未建卡 → 自动创建 SRS 卡片
        if (count >= AUTO_CARD_THRESHOLD && cardExists == 0) {
            VocabularyCard card = new VocabularyCard();
            card.setUserId(userId);
            card.setWord(word);
            card.setPhrase("查词自动导入 — 累计查询 " + count + " 次");
            card.setContextClipId(clipId);
            card.setNextReviewTime(now);         // 立即进入复习队列
            card.setEaseFactor(BigDecimal.valueOf(2.50));
            card.setRepetitions(0);
            card.setReviewInterval(0);
            card.setCreateTime(now);
            card.setUpdateTime(now);
            cardMapper.insert(card);

            log.info("Auto-created SRS card: userId={}, word={}, lookupCount={}", userId, word, count);
        }
    }
}
