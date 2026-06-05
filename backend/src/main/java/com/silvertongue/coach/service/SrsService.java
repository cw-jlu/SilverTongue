package com.silvertongue.coach.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.coach.dto.CardReviewRequest;
import com.silvertongue.coach.dto.CardVO;
import com.silvertongue.coach.entity.VocabularyCard;
import com.silvertongue.coach.mapper.VocabularyCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SrsService {

    private final VocabularyCardMapper cardMapper;

    /**
     * 获取今日待复习卡片
     */
    public List<CardVO> getDueCards(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<VocabularyCard> cards = cardMapper.selectList(new LambdaQueryWrapper<VocabularyCard>()
                .eq(VocabularyCard::getUserId, userId)
                .le(VocabularyCard::getNextReviewTime, now)
                .orderByAsc(VocabularyCard::getNextReviewTime));
        return cards.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 复习反馈 — SuperMemo-2 算法调度
     */
    @Transactional
    public CardVO review(Long userId, CardReviewRequest request) {
        VocabularyCard card = cardMapper.selectById(request.getCardId());
        if (card == null || !card.getUserId().equals(userId)) {
            throw new IllegalArgumentException("card not found");
        }

        int quality = request.getQuality();
        BigDecimal easeFactor = card.getEaseFactor();
        int repetitions = card.getRepetitions();
        int interval = card.getReviewInterval();

        if (quality >= 3) {
            // 通过：递增间隔
            switch (repetitions) {
                case 0 -> interval = 1;
                case 1 -> interval = 6;
                default -> interval = Math.round(interval * easeFactor.floatValue());
            }
            repetitions++;
        } else {
            // 失败：重置
            repetitions = 0;
            interval = 1;
        }

        // 更新 ease_factor
        easeFactor = easeFactor.add(
                BigDecimal.valueOf(0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
        );
        if (easeFactor.compareTo(BigDecimal.valueOf(1.3)) < 0) {
            easeFactor = BigDecimal.valueOf(1.3);
        }

        LocalDateTime nextReview = LocalDateTime.now().plusDays(interval);

        card.setEaseFactor(easeFactor.setScale(2, RoundingMode.HALF_UP));
        card.setRepetitions(repetitions);
        card.setReviewInterval(interval);
        card.setNextReviewTime(nextReview);
        card.setUpdateTime(LocalDateTime.now());
        cardMapper.updateById(card);

        log.info("SRS review: cardId={}, quality={}, ef={}, reps={}, interval={}d, next={}",
                card.getId(), quality, card.getEaseFactor(), repetitions, interval, nextReview);

        return toVO(card);
    }

    private CardVO toVO(VocabularyCard c) {
        return CardVO.builder()
                .id(c.getId())
                .word(c.getWord())
                .phoneticUs(c.getPhoneticUs())
                .dictionarySource(c.getDictionarySource())
                .phrase(c.getPhrase())
                .contextClipId(c.getContextClipId())
                .nextReviewTime(c.getNextReviewTime())
                .easeFactor(c.getEaseFactor())
                .repetitions(c.getRepetitions())
                .reviewInterval(c.getReviewInterval())
                .build();
    }
}
