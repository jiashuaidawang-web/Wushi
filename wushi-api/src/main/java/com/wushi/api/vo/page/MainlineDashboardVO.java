package com.wushi.api.vo.page;

import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.mainline.model.MainlineJudgementDetail;

import java.util.List;

public record MainlineDashboardVO(
        MarketQuery query,
        List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineJudgements,
        String competitionSummary
) {
}
