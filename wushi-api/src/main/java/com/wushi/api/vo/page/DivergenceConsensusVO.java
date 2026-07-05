package com.wushi.api.vo.page;

import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;

import java.util.List;

public record DivergenceConsensusVO(
        MarketQuery query,
        List<JudgmentBlockVO<DivergenceConsensusDetail>> divergenceJudgements,
        String confirmationSummary
) {
}
