package com.silvertongue.coach.service;

import com.silvertongue.coach.dto.CardReviewRequest;
import com.silvertongue.coach.dto.CardVO;
import com.silvertongue.coach.entity.VocabularyCard;
import com.silvertongue.coach.mapper.VocabularyCardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SrsServiceTest {

    @Mock
    private VocabularyCardMapper cardMapper;

    @InjectMocks
    private SrsService srsService;

    @Test
    void reviewShouldAdvanceCardUsingSm2Rules() {
        VocabularyCard card = buildCard(1L, 9L, new BigDecimal("2.50"), 1, 1);
        CardReviewRequest request = new CardReviewRequest();
        request.setCardId(1L);
        request.setQuality(5);

        when(cardMapper.selectById(1L)).thenReturn(card);

        CardVO result = srsService.review(9L, request);

        ArgumentCaptor<VocabularyCard> cardCaptor = ArgumentCaptor.forClass(VocabularyCard.class);
        verify(cardMapper).updateById(cardCaptor.capture());
        VocabularyCard updated = cardCaptor.getValue();

        assertEquals(2, updated.getRepetitions());
        assertEquals(6, updated.getReviewInterval());
        assertEquals(new BigDecimal("2.60"), updated.getEaseFactor());
        assertTrue(updated.getNextReviewTime().isAfter(LocalDateTime.now().plusDays(5)));
        assertEquals(updated.getId(), result.getId());
        assertEquals(updated.getReviewInterval(), result.getReviewInterval());
        assertEquals(updated.getEaseFactor(), result.getEaseFactor());
    }

    @Test
    void reviewShouldResetProgressAndClampEaseFactorOnFailure() {
        VocabularyCard card = buildCard(2L, 9L, new BigDecimal("1.35"), 4, 12);
        CardReviewRequest request = new CardReviewRequest();
        request.setCardId(2L);
        request.setQuality(0);

        when(cardMapper.selectById(2L)).thenReturn(card);

        CardVO result = srsService.review(9L, request);

        assertEquals(0, result.getRepetitions());
        assertEquals(1, result.getReviewInterval());
        assertEquals(new BigDecimal("1.30"), result.getEaseFactor());
    }

    @Test
    void reviewShouldRejectMissingOrForeignCard() {
        CardReviewRequest request = new CardReviewRequest();
        request.setCardId(3L);
        request.setQuality(3);

        when(cardMapper.selectById(3L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> srsService.review(9L, request));

        assertEquals("card not found", exception.getMessage());
        verify(cardMapper, never()).updateById(any(VocabularyCard.class));
    }

    @Test
    void getDueCardsShouldMapEntitiesToViewObjects() {
        VocabularyCard card = buildCard(4L, 12L, new BigDecimal("2.10"), 2, 3);
        card.setWord("phrase");
        card.setPhoneticUs("freiz");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        List<CardVO> dueCards = srsService.getDueCards(12L);

        assertEquals(1, dueCards.size());
        assertEquals("phrase", dueCards.get(0).getWord());
        assertEquals(new BigDecimal("2.10"), dueCards.get(0).getEaseFactor());
    }

    private VocabularyCard buildCard(Long id, Long userId, BigDecimal easeFactor, int repetitions, int interval) {
        VocabularyCard card = new VocabularyCard();
        card.setId(id);
        card.setUserId(userId);
        card.setWord("word-" + id);
        card.setEaseFactor(easeFactor);
        card.setRepetitions(repetitions);
        card.setReviewInterval(interval);
        card.setNextReviewTime(LocalDateTime.now().minusDays(1));
        return card;
    }
}
