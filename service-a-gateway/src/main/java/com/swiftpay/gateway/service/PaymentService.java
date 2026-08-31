package com.swiftpay.gateway.service;

import com.swiftpay.events.PaymentInitiatedEvent;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.kafka.PaymentEventProducer;
import com.swiftpay.gateway.model.Payment;
import com.swiftpay.gateway.model.PaymentStatus;
import com.swiftpay.gateway.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:txn:";

    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentEventProducer paymentEventProducer;
    private final long idempotencyTtlHours;

    public PaymentService(PaymentRepository paymentRepository,
                          RedisTemplate<String, String> redisTemplate,
                          PaymentEventProducer paymentEventProducer,
                          @Value("${swiftpay.idempotency.ttl-hours:24}") long idempotencyTtlHours) {
        this.paymentRepository = paymentRepository;
        this.redisTemplate = redisTemplate;
        this.paymentEventProducer = paymentEventProducer;
        this.idempotencyTtlHours = idempotencyTtlHours;
    }

    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + request.getTransactionId();

        Boolean isNew = redisTemplate.opsForValue()
            .setIfAbsent(idempotencyKey, "PROCESSING", Duration.ofHours(idempotencyTtlHours));

        if (Boolean.FALSE.equals(isNew)) {
            Optional<Payment> existing = paymentRepository.findByTransactionId(request.getTransactionId());
            if (existing.isPresent()) {
                log.info("Duplicate transaction detected, returning existing record for transactionId={}",
                    request.getTransactionId());
                return toResponse(existing.get());
            }
            // Idempotency key is present but no payment row exists (e.g. Redis outlived a DB reset,
            // or a prior attempt failed before the commit). Treat it as a fresh request.
            log.warn("Stale idempotency key with no payment record for transactionId={}, reprocessing",
                request.getTransactionId());
        }

        Payment payment = new Payment(
            request.getTransactionId(),
            request.getSenderId(),
            request.getReceiverId(),
            request.getAmount(),
            request.getCurrency(),
            PaymentStatus.PENDING
        );
        // Payment has an assigned (non-generated) id, so save() would route through merge();
        // keep the returned managed instance so @PrePersist-populated timestamps are visible.
        Payment saved = paymentRepository.saveAndFlush(payment);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
            saved.getTransactionId(),
            saved.getSenderId(),
            saved.getReceiverId(),
            saved.getAmount(),
            saved.getCurrency(),
            Instant.now()
        );
        paymentEventProducer.publishPaymentInitiated(event);

        log.info("Payment initiated: transactionId={}", saved.getTransactionId());
        return toResponse(saved);
    }

    public PaymentResponse getPayment(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
            .map(this::toResponse)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Payment not found: " + transactionId));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getTransactionId(),
            payment.getSenderId(),
            payment.getReceiverId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getCreatedAt()
        );
    }
}
