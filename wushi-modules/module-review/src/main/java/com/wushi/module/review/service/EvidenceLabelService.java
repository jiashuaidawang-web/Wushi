package com.wushi.module.review.service;

import com.wushi.module.review.model.EvidenceLabelRequest;
import com.wushi.module.review.model.EvidenceLabelResultInfo;

public interface EvidenceLabelService {

    EvidenceLabelResultInfo label(EvidenceLabelRequest request);
}
