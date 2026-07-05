package com.wushi.module.leader.engine;

import com.wushi.common.model.JudgementResult;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.engine.core.JudgementEngine;

import java.util.List;

public interface LeaderCompetitionEngine extends JudgementEngine<LeaderJudgementDetail> {

    List<JudgementResult<LeaderJudgementDetail>> judgeCandidates(EngineRequest request, int candidateLimit);
}
