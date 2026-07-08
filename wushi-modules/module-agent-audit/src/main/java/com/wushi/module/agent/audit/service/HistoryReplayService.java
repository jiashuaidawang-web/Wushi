package com.wushi.module.agent.audit.service;

import com.wushi.module.agent.audit.model.HistoryReplayRequest;
import com.wushi.module.agent.audit.model.HistoryReplayResult;

public interface HistoryReplayService {

    HistoryReplayResult replay(HistoryReplayRequest request);
}
