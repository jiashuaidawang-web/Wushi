package com.wushi.module.review.service.impl;

import com.wushi.module.review.model.HistoricalSampleConfirmationRequest;
import com.wushi.module.review.model.HistoricalSampleConfirmationResult;
import com.wushi.module.review.service.HistoricalSampleConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultHistoricalSampleConfirmationService implements HistoricalSampleConfirmationService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public HistoricalSampleConfirmationResult confirm(HistoricalSampleConfirmationRequest request) {
        String sampleId = StringUtils.hasText(request.sampleId()) ? request.sampleId() : "SAMPLE-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into historical_sample_confirmation
                (sample_id, trade_date, target_type, target_code, sample_type, confirmed_label,
                 sample_quality, confirmation_desc, reviewer)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update confirmed_label = values(confirmed_label), sample_quality = values(sample_quality),
                confirmation_desc = values(confirmation_desc), reviewer = values(reviewer), updated_at = current_timestamp
                """, sampleId,
                request.tradeDate() == null ? LocalDate.now() : request.tradeDate(),
                request.targetType().name(),
                StringUtils.hasText(request.targetCode()) ? request.targetCode() : "MARKET",
                request.sampleType().name(),
                request.confirmedLabel(),
                request.sampleQuality().name(),
                request.confirmationDesc(),
                StringUtils.hasText(request.reviewer()) ? request.reviewer() : "manual");
        return new HistoricalSampleConfirmationResult(sampleId, request.sampleType(), request.confirmedLabel(), request.sampleQuality(), LocalDateTime.now());
    }
}
