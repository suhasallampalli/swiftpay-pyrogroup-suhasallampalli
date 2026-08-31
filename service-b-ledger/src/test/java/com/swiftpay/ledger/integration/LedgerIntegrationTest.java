package com.swiftpay.ledger.integration;

import com.swiftpay.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.model.TransactionStatus;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import com.swiftpay.ledger.service.LedgerService;
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
@EmbeddedKafka(partitions = 1,
    topics = {"payment.initiated.test", "payment.completed.test", "payment.failed.test"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext
class LedgerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired LedgerService ledgerService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;

    @BeforeEach
    void seedAccounts() {
        if (accountRepository.findByUserId("user-001").isEmpty()) {
            accountRepository.save(new com.swiftpay.ledger.model.Account("user-001", new BigDecimal("10000.00"), "USD"));
        }
        if (accountRepository.findByUserId("user-002").isEmpty()) {
            accountRepository.save(new com.swiftpay.ledger.model.Account("user-002", new BigDecimal("5000.00"), "USD"));
        }
        if (accountRepository.findByUserId("user-poor").isEmpty()) {
            accountRepository.save(new com.swiftpay.ledger.model.Account("user-poor", new BigDecimal("10.00"), "USD"));
        }
    }

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
    @DisplayName("FUNCTIONAL: processPayment deducts sender and credits receiver")
    void functionalTest_processPayment_balancesUpdated() {
        String txnId = UUID.randomUUID().toString();
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
            txnId, "user-001", "user-002", new BigDecimal("100.00"), "USD", Instant.now());

        ledgerService.processPayment(event);

        assertThat(accountRepository.findByUserId("user-001"))
            .isPresent()
            .hasValueSatisfying(a -> assertThat(a.getBalance()).isLessThan(new BigDecimal("10000.00")));
        assertThat(accountRepository.findByUserId("user-002"))
            .isPresent()
            .hasValueSatisfying(a -> assertThat(a.getBalance()).isGreaterThan(new BigDecimal("5000.00")));
        assertThat(transactionRepository.findByTransactionId(txnId))
            .isPresent()
            .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.COMPLETED));
    }

    @Test
    @Order(3)
    @DisplayName("NEGATIVE: insufficient funds results in FAILED transaction")
    void negativeTest_insufficientFunds_transactionFailed() {
        String txnId = UUID.randomUUID().toString();
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
            txnId, "user-poor", "user-001", new BigDecimal("999.00"), "USD", Instant.now());

        ledgerService.processPayment(event);

        assertThat(transactionRepository.findByTransactionId(txnId))
            .isPresent()
            .hasValueSatisfying(t -> {
                assertThat(t.getStatus()).isEqualTo(TransactionStatus.FAILED);
                assertThat(t.getFailureReason()).contains("Insufficient funds");
            });
    }

    @Test
    @Order(4)
    @DisplayName("INTEGRATION: GET /v1/users/{userId}/transactions returns history")
    void integrationTest_transactionHistory() throws Exception {
        String txnId = UUID.randomUUID().toString();
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
            txnId, "user-001", "user-002", new BigDecimal("50.00"), "USD", Instant.now());
        ledgerService.processPayment(event);

        mockMvc.perform(get("/v1/users/user-001/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(5)
    @DisplayName("REGRESSION: duplicate event processing is idempotent")
    void regressionTest_duplicateEventIdempotent() {
        String txnId = UUID.randomUUID().toString();
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
            txnId, "user-001", "user-002", new BigDecimal("25.00"), "USD", Instant.now());

        ledgerService.processPayment(event);
        ledgerService.processPayment(event);

        long count = transactionRepository.findAll().stream()
            .filter(t -> t.getTransactionId().equals(txnId))
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("NEGATIVE: GET /v1/users/{userId}/transactions for unknown user returns empty list")
    void negativeTest_unknownUserTransactionHistory_returnsEmpty() throws Exception {
        mockMvc.perform(get("/v1/users/user-nonexistent/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(7)
    @DisplayName("NEGATIVE: GET /v1/transactions/{id} for unknown txn returns 404")
    void negativeTest_getUnknownTransaction_returns404() throws Exception {
        mockMvc.perform(get("/v1/transactions/txn-does-not-exist"))
            .andExpect(status().isNotFound());
    }
}
