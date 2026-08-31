package com.swiftpay.gateway.unit;

import com.swiftpay.events.PaymentInitiatedEvent;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.kafka.PaymentEventProducer;
import com.swiftpay.gateway.model.Payment;
import com.swiftpay.gateway.model.PaymentStatus;
import com.swiftpay.gateway.repository.PaymentRepository;
import com.swiftpay.gateway.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private PaymentEventProducer paymentEventProducer;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        paymentService = new PaymentService(paymentRepository, redisTemplate, paymentEventProducer, 24L);
    }

    @Test
    @DisplayName("initiatePayment - happy path saves payment and publishes event")
    void initiatePayment_happyPath() {
        PaymentRequest req = buildRequest("txn-001", "user-001", "user-002", "100.00", "USD");
        given(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).willReturn(true);

        Payment savedPayment = new Payment("txn-001", "user-001", "user-002",
            new BigDecimal("100.00"), "USD", PaymentStatus.PENDING);
        given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

        PaymentResponse response = paymentService.initiatePayment(req);

        assertThat(response.getTransactionId()).isEqualTo("txn-001");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);

        ArgumentCaptor<PaymentInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentInitiatedEvent.class);
        verify(paymentEventProducer).publishPaymentInitiated(eventCaptor.capture());
        PaymentInitiatedEvent event = eventCaptor.getValue();
        assertThat(event.getTransactionId()).isEqualTo("txn-001");
        assertThat(event.getSenderId()).isEqualTo("user-001");
        assertThat(event.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("initiatePayment - duplicate transaction returns existing record")
    void initiatePayment_duplicate_returnsExisting() {
        PaymentRequest req = buildRequest("txn-dup", "user-001", "user-002", "50.00", "USD");
        given(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).willReturn(false);

        Payment existing = new Payment("txn-dup", "user-001", "user-002",
            new BigDecimal("50.00"), "USD", PaymentStatus.PENDING);
        given(paymentRepository.findByTransactionId("txn-dup")).willReturn(Optional.of(existing));

        PaymentResponse response = paymentService.initiatePayment(req);

        assertThat(response.getTransactionId()).isEqualTo("txn-dup");
        verify(paymentRepository, never()).save(any());
        verify(paymentEventProducer, never()).publishPaymentInitiated(any());
    }

    @Test
    @DisplayName("getPayment - returns existing payment")
    void getPayment_found() {
        Payment p = new Payment("txn-001", "user-001", "user-002",
            new BigDecimal("100.00"), "USD", PaymentStatus.COMPLETED);
        given(paymentRepository.findByTransactionId("txn-001")).willReturn(Optional.of(p));

        PaymentResponse response = paymentService.getPayment("txn-001");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("getPayment - throws EntityNotFoundException when not found")
    void getPayment_notFound_throws() {
        given(paymentRepository.findByTransactionId("txn-missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment("txn-missing"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    private PaymentRequest buildRequest(String txnId, String sender, String receiver,
                                        String amount, String currency) {
        PaymentRequest req = new PaymentRequest();
        req.setTransactionId(txnId);
        req.setSenderId(sender);
        req.setReceiverId(receiver);
        req.setAmount(new BigDecimal(amount));
        req.setCurrency(currency);
        return req;
    }
}
