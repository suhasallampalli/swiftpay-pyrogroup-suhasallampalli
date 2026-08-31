package com.swiftpay.ledger.kafka;

import com.swiftpay.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class PaymentInitiatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentInitiatedConsumer.class);

    private final LedgerService ledgerService;

    public PaymentInitiatedConsumer(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        autoCreateTopics = "true"
    )
    @KafkaListener(
        topics = "${swiftpay.kafka.topics.payment-initiated}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Received PaymentInitiated event: transactionId={}", event.getTransactionId());
        ledgerService.processPayment(event);
    }
}
