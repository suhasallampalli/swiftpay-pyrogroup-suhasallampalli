package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.model.AnalyticsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsRepository extends JpaRepository<AnalyticsRecord, Long> {

    Optional<AnalyticsRecord> findByTransactionId(String transactionId);

    @Query("SELECT COUNT(a) FROM AnalyticsRecord a WHERE a.eventType = :eventType")
    long countByEventType(String eventType);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM AnalyticsRecord a WHERE a.eventType = 'COMPLETED'")
    BigDecimal totalCompletedVolume();

    List<AnalyticsRecord> findTop100ByOrderByEventTimestampDesc();
}
