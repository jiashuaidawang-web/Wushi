package com.wushi.module.backtest.engine;

import com.wushi.module.backtest.model.ExperienceUpdateRequest;
import com.wushi.module.backtest.model.ExperienceUpdateResult;

public interface ExperienceFactorEngine {

    ExperienceUpdateResult updateExperience(ExperienceUpdateRequest request);
}
