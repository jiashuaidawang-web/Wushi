package com.wushi.module.emotion.model;

import com.wushi.common.enums.EmotionCycleStage;
import com.wushi.common.enums.MarketCycleStage;

import java.math.BigDecimal;

public record CycleJudgementDetail(
        MarketCycleStage marketCycleStage,
        EmotionCycleStage emotionCycleStage,
        BigDecimal moneyEffectScore,
        BigDecimal lossEffectScore,
        BigDecimal stageScore,
        String transitionSignal,
        String stageReason
) {
}
