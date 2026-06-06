package com.silvertongue.coach.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.coach.entity.VocabularyCard;
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
public class ChinglishCardService {

    private final VocabularyCardMapper cardMapper;

    /**
     * 将 Chinglish 检测结果自动创建为 SRS 复习卡片。
     * 去重：同一用户同一原始表达只建一张卡片。
     */
    @Transactional
    public void createFromChinglish(Long userId, String originalExpression,
                                     String correction, String errorPattern, String context) {
        // 去重检查
        long exists = cardMapper.selectCount(
                new LambdaQueryWrapper<VocabularyCard>()
                        .eq(VocabularyCard::getUserId, userId)
                        .eq(VocabularyCard::getWord, originalExpression)
        );

        if (exists > 0) {
            log.debug("Chinglish card already exists: userId={}, expression={}", userId, originalExpression);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String phrase = String.format("❌ %s → ✅ %s [%s]", originalExpression, correction,
                errorPattern != null ? errorPattern : "Chinglish");

        VocabularyCard card = new VocabularyCard();
        card.setUserId(userId);
        card.setWord(originalExpression);
        card.setPhrase(phrase);
        card.setNextReviewTime(now);          // 立即进入复习队列
        card.setEaseFactor(BigDecimal.valueOf(2.50));
        card.setRepetitions(0);
        card.setReviewInterval(0);
        card.setCreateTime(now);
        card.setUpdateTime(now);
        cardMapper.insert(card);

        log.info("Auto-created Chinglish SRS card: userId={}, expression={}, pattern={}",
                userId, originalExpression, errorPattern);
    }
}
