package com.wushi.api.vo.page;

import com.wushi.api.vo.common.*;
import com.wushi.module.emotion.model.CycleJudgementDetail;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
import com.wushi.module.risk.model.RiskRadarDetail;

import java.util.List;

public record MarketOverviewVO(
        MarketQuery query,
        JudgmentBlockVO<CycleJudgementDetail> cycleCard,
        List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineCards,
        List<JudgmentBlockVO<LeaderJudgementDetail>> leaderCards,
        JudgmentBlockVO<DivergenceConsensusDetail> divergenceCard,
        JudgmentBlockVO<RiskRadarDetail> riskCard,
        List<NextWatchItemVO> nextWatchList,
        List<DataQualityIssueVO> dataQualityIssues
) {
}
