package com.wushi.module.rule.service;

import com.wushi.common.enums.EngineType;
import com.wushi.module.rule.domain.entity.DataQualityImpactConfigEntity;
import com.wushi.module.rule.model.DashboardCardConfig;
import com.wushi.module.rule.model.ResolvedRuleConfig;

import java.util.List;
import java.util.Optional;

public interface RuleConfigService {

    ResolvedRuleConfig resolve(EngineType engineType, String requestedRuleVersion);

    Optional<String> findActiveRuleVersion(EngineType engineType);

    List<DataQualityImpactConfigEntity> listDataQualityImpactConfigs();

    List<DataQualityImpactConfigEntity> listDataQualityImpactConfigs(String tableName);

    List<DashboardCardConfig> listDashboardCards(String pageCode);
}
