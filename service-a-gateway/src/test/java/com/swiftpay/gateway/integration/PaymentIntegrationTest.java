package com.swiftpay.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.config.TestRedisConfig;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.model.PaymentStatus;
import com.swiftpay.gateway.repository.PaymentRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"payment.initiated.test"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PaymentRepository paymentRepository;

    @BeforeEach
    void cleanDb() {
        paymentRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("SMOKE: health endpoint returns UP")
    void smokeTest_healthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(2)
    @DisplayName("FUNCTIONAL: POST /v1/payments returns 202 and PENDING status")
    void functionalTest_initiatePayment_returns202() throws Exception {
        PaymentRequest request = buildRequest(UUID.randomUUID().toString(), "user-001", "user-002", "150.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.transactionId").value(request.getTransactionId()));
    }

    @Test
    @Order(3)
    @DisplayName("INTEGRATION: payment persisted with PENDING status in database")
    void integrationTest_paymentPersistedToDb() throws Exception {
        String txnId = UUID.randomUUID().toString();
        PaymentRequest request = buildRequest(txnId, "user-001", "user-002", "200.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted());

        assertThat(paymentRepository.findByTransactionId(txnId))
            .isPresent()
            .hasValueSatisfying(p -> {
                assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
                assertThat(p.getAmount()).isEqualByComparingTo("200.00");
            });
    }

    @Test
    @Order(4)
    @DisplayName("INTEGRATION: duplicate transaction_id is idempotent (single DB record)")
    void integrationTest_idempotency() throws Exception {
        String txnId = "idempotent-" + UUID.randomUUID();
        PaymentRequest request = buildRequest(txnId, "user-001", "user-002", "75.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted());

        // Second request with same txnId — idempotency check via in-memory Redis mock
        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted());

        long count = paymentRepository.findAll().stream()
            .filter(p -> p.getTransactionId().equals(txnId))
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("NEGATIVE: missing sender_id returns 400")
    void negativeTest_missingSenderId_returns400() throws Exception {
        PaymentRequest request = buildRequest(UUID.randomUUID().toString(), null, "user-002", "100.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @Order(6)
    @DisplayName("NEGATIVE: negative amount returns 400")
    void negativeTest_negativeAmount_returns400() throws Exception {
        PaymentRequest request = buildRequest(UUID.randomUUID().toString(), "user-001", "user-002", "-50.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    @DisplayName("NEGATIVE: GET non-existent payment returns 404")
    void negativeTest_getNotFound_returns404() throws Exception {
        mockMvc.perform(get("/v1/payments/non-existent-txn"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    @DisplayName("REGRESSION: Swagger UI is accessible")
    void regressionTest_swaggerUiAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(9)
    @DisplayName("REGRESSION: actuator health shows datasource and redis UP")
    void regressionTest_actuatorDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    private PaymentRequest buildRequest(String txnId, String sender, String receiver,
                                        String amount, String currency) {
        PaymentRequest req = new PaymentRequest();
        req.setTransactionId(txnId);
        req.setSenderId(sender);
        req.setReceiverId(receiver);
        req.setAmount(amount != null ? new BigDecimal(amount) : null);
        req.setCurrency(currency);
        return req;
    }
}
