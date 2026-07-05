package com.wushi.module.mainline.service;

import com.wushi.module.mainline.model.MainlineCandidate;

import java.time.LocalDate;
import java.util.List;

public interface MainlineCandidateSelector {

    List<MainlineCandidate> selectCandidates(LocalDate tradeDate, int limit);
}
