package com.wushi.module.backtest.engine;

import com.wushi.common.model.ForwardValidationResult;
import com.wushi.module.backtest.model.ForwardValidationRequest;

import java.util.List;

public interface ForwardValidationEngine {

    ForwardValidationResult validate(ForwardValidationRequest request);

    List<ForwardValidationResult> validateBatch(List<ForwardValidationRequest> requests);
}
