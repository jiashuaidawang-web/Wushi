package com.wushi.module.similarity.engine;

import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.similarity.model.HistoricalSimilarityMatch;

import java.util.List;

public interface HistoricalSimilarityEngine {

    List<HistoricalSimilarityMatch> match(EngineRequest request);
}
