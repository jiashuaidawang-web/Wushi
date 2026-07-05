package com.wushi.api.vo.page;

import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.emotion.model.CycleJudgementDetail;

public record CycleDashboardVO(
        MarketQuery query,
        JudgmentBlockVO<CycleJudgementDetail> cycleJudgement,
        String cyclePathSummary
) {
}
