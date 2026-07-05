package com.wushi.module.rule.quality;

import com.wushi.common.model.DataQualityContext;

import java.time.LocalDate;
import java.util.Collection;

public interface DataQualityAssessor {

    DataQualityContext assess(LocalDate tradeDate, Collection<String> requiredTables);

    DataQualityContext assessForPage(LocalDate tradeDate, String pageCode);
}
