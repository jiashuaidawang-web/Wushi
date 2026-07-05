package com.wushi.api.vo.page;

import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.leader.model.LeaderJudgementDetail;

import java.util.List;

public record LeaderCompetitionVO(
        MarketQuery query,
        List<JudgmentBlockVO<LeaderJudgementDetail>> leaderJudgements,
        String competitionSummary
) {
}
