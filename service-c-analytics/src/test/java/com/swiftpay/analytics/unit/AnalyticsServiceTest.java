package com.swiftpay.analytics.unit;

import com.swiftpay.analytics.model.AnalyticsRecord;
import com.swiftpay.analytics.repository.AnalyticsRepository;
import com.swiftpay.analytics.service.AnalyticsService;
import com.swiftpay.events.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private AnalyticsRepository analyticsRepository;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(analyticsRepository);
    }

    @Test
    @DisplayName("recordPaymentCompleted - saves new analytics record")
    void recordPaymentCompleted_savesRecord() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            "txn-001", "user-001", "user-002", new BigDecimal("100.00"), "USD", Instant.now());
        given(analyticsRepository.findByTransactionId("txn-001")).willReturn(Optional.empty());
        given(analyticsRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        analyticsService.recordPaymentCompleted(event);

        ArgumentCaptor<AnalyticsRecord> captor = ArgumentCaptor.forClass(AnalyticsRecord.class);
        verify(analyticsRepository).save(captor.capture());
        AnalyticsRecord saved = captor.getValue();
        assertThat(saved.getTransactionId()).isEqualTo("txn-001");
        assertThat(saved.getEventType()).isEqualTo("COMPLETED");
        assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("recordPaymentCompleted - skips duplicate event")
    void recordPaymentCompleted_duplicate_skipped() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            "txn-dup", "user-001", "user-002", new BigDecimal("50.00"), "USD", Instant.now());
        given(analyticsRepository.findByTransactionId("txn-dup"))
            .willReturn(Optional.of(new AnalyticsRecord()));

        analyticsService.recordPaymentCompleted(event);

        verify(analyticsRepository, never()).save(any());
    }
}
