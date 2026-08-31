package com.swiftpay.analytics.service;

import com.swiftpay.analytics.model.AnalyticsRecord;
import com.swiftpay.analytics.repository.AnalyticsRepository;
import com.swiftpay.events.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional
    public void recordPaymentCompleted(PaymentCompletedEvent event) {
        if (analyticsRepository.findByTransactionId(event.getTransactionId()).isPresent()) {
            log.warn("Analytics record already exists for transactionId={}", event.getTransactionId());
            return;
        }

        AnalyticsRecord record = new AnalyticsRecord(
            event.getTransactionId(),
            event.getSenderId(),
            event.getReceiverId(),
            event.getAmount(),
            event.getCurrency(),
            "COMPLETED",
            event.getTimestamp()
        );
        analyticsRepository.save(record);
        log.info("Analytics recorded: transactionId={}", event.getTransactionId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        long total = analyticsRepository.count();
        long completed = analyticsRepository.countByEventType("COMPLETED");
        BigDecimal volume = analyticsRepository.totalCompletedVolume();
        return Map.of(
            "totalEvents", total,
            "completedPayments", completed,
            "totalVolume", volume
        );
    }

    @Transactional(readOnly = true)
    public List<AnalyticsRecord> getRecentEvents() {
        return analyticsRepository.findTop100ByOrderByEventTimestampDesc();
    }
}
