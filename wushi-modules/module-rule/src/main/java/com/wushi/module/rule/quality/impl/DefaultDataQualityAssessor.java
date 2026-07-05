package com.wushi.module.rule.quality.impl;

import com.wushi.common.enums.DataQualityLevel;
import com.wushi.common.model.DataQualityContext;
import com.wushi.common.model.DataQualityIssue;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.rule.domain.entity.DataQualityImpactConfigEntity;
import com.wushi.module.rule.quality.DataQualityAssessor;
import com.wushi.module.rule.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultDataQualityAssessor implements DataQualityAssessor {

    private static final BigDecimal MEDIUM_PENALTY = new BigDecimal("0.1500");
    private static final BigDecimal LOW_PENALTY = new BigDecimal("0.3500");
    private static final BigDecimal INSUFFICIENT_PENALTY = new BigDecimal("0.6000");

    private final RuleConfigService ruleConfigService;
    private final MarketFactService marketFactService;

    @Override
    public DataQualityContext assess(LocalDate tradeDate, Collection<String> requiredTables) {
        Set<String> required = normalize(requiredTables);
        List<DataQualityImpactConfigEntity> configs = ruleConfigService.listDataQualityImpactConfigs().stream()
                .filter(config -> required.isEmpty() || required.contains(normalizeTableName(config.getTableName())))
                .toList();
        return assessByConfigs(tradeDate, configs);
    }

    @Override
    public DataQualityContext assessForPage(LocalDate tradeDate, String pageCode) {
        List<DataQualityImpactConfigEntity> configs = ruleConfigService.listDataQualityImpactConfigs().stream()
                .filter(config -> parseStringList(config.getImpactPages()).contains(pageCode))
                .toList();
        return assessByConfigs(tradeDate, configs);
    }

    private DataQualityContext assessByConfigs(LocalDate tradeDate, List<DataQualityImpactConfigEntity> configs) {
        List<DataQualityIssue> issues = new ArrayList<>();
        BigDecimal totalPenalty = BigDecimal.ZERO;

        for (DataQualityImpactConfigEntity config : configs) {
            Optional<FactTable> factTable = resolveFactTable(config.getTableName());
            boolean missing = factTable.isEmpty() || isFactMissing(factTable.get(), tradeDate);
            if (!missing) {
                continue;
            }
            BigDecimal penalty = config.getConfidencePenalty() == null ? BigDecimal.ZERO : config.getConfidencePenalty();
            totalPenalty = totalPenalty.add(penalty);
            issues.add(DataQualityIssue.builder()
                    .issueId("DQ-" + normalizeTableName(config.getTableName()) + "-" + tradeDate)
                    .dataDomain(config.getDataDomain())
                    .tableName(config.getTableName())
                    .issueType("MISSING_FACT")
                    .severity(resolveSeverity(penalty))
                    .impactLevel(resolveImpactLevel(penalty))
                    .impactPages(parseStringList(config.getImpactPages()))
                    .confidencePenalty(penalty)
                    .description(config.getImpactDesc())
                    .build());
        }

        BigDecimal cappedPenalty = totalPenalty.min(BigDecimal.ONE);
        return DataQualityContext.builder()
                .tradeDate(tradeDate)
                .level(resolveLevel(cappedPenalty, issues))
                .confidencePenalty(cappedPenalty)
                .issueList(issues)
                .build();
    }

    private boolean isFactMissing(FactTable factTable, LocalDate tradeDate) {
        if (!factTable.hasTradeDate()) {
            return false;
        }
        try {
            return CollectionUtils.isEmpty(marketFactService.findByTradeDate(factTable, tradeDate));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private Optional<FactTable> resolveFactTable(String tableName) {
        String normalized = normalizeTableName(tableName);
        for (FactTable factTable : FactTable.values()) {
            if (normalizeTableName(factTable.tableName()).equals(normalized)) {
                return Optional.of(factTable);
            }
        }
        return Optional.empty();
    }

    private Set<String> normalize(Collection<String> tableNames) {
        if (tableNames == null) {
            return Set.of();
        }
        return tableNames.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeTableName)
                .collect(Collectors.toSet());
    }

    private String normalizeTableName(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return "";
        }
        String plainName = tableName.contains(".")
                ? tableName.substring(tableName.lastIndexOf('.') + 1)
                : tableName;
        return plainName.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of();
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return List.of(body.split(",")).stream()
                .map(String::trim)
                .map(value -> value.replaceAll("^\"|\"$", ""))
                .filter(StringUtils::hasText)
                .toList();
    }

    private DataQualityLevel resolveLevel(BigDecimal penalty, List<DataQualityIssue> issues) {
        if (issues.isEmpty()) {
            return DataQualityLevel.HIGH;
        }
        if (penalty.compareTo(INSUFFICIENT_PENALTY) >= 0) {
            return DataQualityLevel.INSUFFICIENT;
        }
        if (penalty.compareTo(LOW_PENALTY) >= 0) {
            return DataQualityLevel.LOW;
        }
        if (penalty.compareTo(MEDIUM_PENALTY) >= 0) {
            return DataQualityLevel.MEDIUM;
        }
        return DataQualityLevel.HIGH;
    }

    private String resolveSeverity(BigDecimal penalty) {
        if (penalty.compareTo(new BigDecimal("0.3000")) >= 0) {
            return "HIGH";
        }
        if (penalty.compareTo(new BigDecimal("0.1500")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveImpactLevel(BigDecimal penalty) {
        if (penalty.compareTo(new BigDecimal("0.3000")) >= 0) {
            return "CORE";
        }
        if (penalty.compareTo(new BigDecimal("0.1500")) >= 0) {
            return "IMPORTANT";
        }
        return "AUXILIARY";
    }
}
