package com.wushi.module.review.service;

import com.wushi.module.review.model.HistoricalSampleConfirmationRequest;
import com.wushi.module.review.model.HistoricalSampleConfirmationResult;

public interface HistoricalSampleConfirmationService {

    HistoricalSampleConfirmationResult confirm(HistoricalSampleConfirmationRequest request);
}
