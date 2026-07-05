package com.wushi.module.review.service;

import com.wushi.module.review.model.ManualCorrectionRequest;
import com.wushi.module.review.model.ManualCorrectionResult;

public interface ManualCorrectionService {

    ManualCorrectionResult correct(ManualCorrectionRequest request);

    ManualCorrectionResult revoke(String correctionId, String reviewer, String reason);
}
