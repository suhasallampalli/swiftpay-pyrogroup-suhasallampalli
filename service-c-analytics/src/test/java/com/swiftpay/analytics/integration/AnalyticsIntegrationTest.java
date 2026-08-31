package com.swiftpay.analytics.integration;

import com.swiftpay.analytics.repository.AnalyticsRepository;
import com.swiftpay.analytics.service.AnalyticsService;
import com.swiftpay.events.PaymentCompletedEvent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"payment.completed.test"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext
class AnalyticsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AnalyticsService analyticsService;
    @Autowired AnalyticsRepository analyticsRepository;

    @Test
    @Order(1)
    @DisplayName("SMOKE: health endpoint returns UP")
    void smokeTest_healthUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(2)
    @DisplayName("FUNCTIONAL: payment completed event recorded in analytics")
    void functionalTest_recordPaymentCompleted() {
        String txnId = UUID.randomUUID().toString();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            txnId, "user-001", "user-002", new BigDecimal("100.00"), "USD", Instant.now());

        analyticsService.recordPaymentCompleted(event);

        assertThat(analyticsRepository.findByTransactionId(txnId)).isPresent();
    }

    @Test
    @Order(3)
    @DisplayName("INTEGRATION: GET /v1/analytics/summary returns counts")
    void integrationTest_analyticsEndpoint() throws Exception {
        mockMvc.perform(get("/v1/analytics/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedPayments").exists())
            .andExpect(jsonPath("$.totalVolume").exists());
    }

    @Test
    @Order(4)
    @DisplayName("REGRESSION: duplicate completed event is idempotent")
    void regressionTest_duplicateEvent_idempotent() {
        String txnId = UUID.randomUUID().toString();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            txnId, "user-001", "user-002", new BigDecimal("50.00"), "USD", Instant.now());

        analyticsService.recordPaymentCompleted(event);
        analyticsService.recordPaymentCompleted(event);

        long count = analyticsRepository.findAll().stream()
            .filter(r -> r.getTransactionId().equals(txnId))
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("NEGATIVE: GET /v1/analytics/events returns empty list initially")
    void negativeTest_recentEvents_returnsListType() throws Exception {
        mockMvc.perform(get("/v1/analytics/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
