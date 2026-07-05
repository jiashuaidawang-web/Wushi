package com.wushi.api.vo.page;

import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.risk.model.RiskRadarDetail;

import java.util.List;

public record RiskRadarVO(
        MarketQuery query,
        List<JudgmentBlockVO<RiskRadarDetail>> riskJudgements,
        String riskSummary
) {
}
