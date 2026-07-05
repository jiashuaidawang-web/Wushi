package com.wushi.module.rule.engine.core;

import com.wushi.common.model.JudgementResult;

public interface JudgementEngine<T> {

    JudgementResult<T> judge(EngineRequest request);
}
