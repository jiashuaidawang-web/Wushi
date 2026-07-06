package com.wushi.module.emotion.model;

import com.wushi.common.enums.EmotionCycleStage;
import com.wushi.common.enums.MarketCycleStage;
import com.wushi.common.model.FactorResult;

import java.math.BigDecimal;
import java.util.List;

public record CycleJudgementDetail(
        MarketCycleStage marketCycleStage,
        EmotionCycleStage emotionCycleStage,
        BigDecimal moneyEffectScore,
        BigDecimal lossEffectScore,
        BigDecimal stageScore,
        String transitionSignal,
        String stageReason,
        String strategyBoundary,
        String allowedMode,
        String falseSignalRisk,
        List<FactorResult> factorResults
) {
}
