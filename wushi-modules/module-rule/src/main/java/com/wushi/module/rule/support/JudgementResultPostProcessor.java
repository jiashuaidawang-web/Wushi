package com.wushi.module.rule.support;

import com.wushi.common.model.JudgementResult;
import com.wushi.module.rule.engine.core.EngineRequest;

public interface JudgementResultPostProcessor {

    <T> JudgementResult<T> apply(EngineRequest request, JudgementResult<T> result);
}
