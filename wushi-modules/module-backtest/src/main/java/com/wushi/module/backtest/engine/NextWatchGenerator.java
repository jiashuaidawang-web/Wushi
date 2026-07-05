package com.wushi.module.backtest.engine;

import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;

import java.util.List;

public interface NextWatchGenerator {

    List<NextWatchItem> generate(JudgementResult<?> judgementResult);

    List<NextWatchItem> generateAll(List<JudgementResult<?>> judgementResults);
}
