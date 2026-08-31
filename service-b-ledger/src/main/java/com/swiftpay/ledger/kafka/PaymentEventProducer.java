package com.swiftpay.ledger.kafka;

import com.swiftpay.events.PaymentCompletedEvent;
import com.swiftpay.events.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicPaymentCompleted;
    private final String topicPaymentFailed;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                 @Value("${swiftpay.kafka.topics.payment-completed}") String topicPaymentCompleted,
                                 @Value("${swiftpay.kafka.topics.payment-failed}") String topicPaymentFailed) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicPaymentCompleted = topicPaymentCompleted;
        this.topicPaymentFailed = topicPaymentFailed;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Publishing PaymentCompleted event: transactionId={}", event.getTransactionId());
        kafkaTemplate.send(topicPaymentCompleted, event.getTransactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PaymentCompleted: transactionId={}", event.getTransactionId(), ex);
                }
            });
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        log.info("Publishing PaymentFailed event: transactionId={}, reason={}", event.getTransactionId(), event.getReason());
        kafkaTemplate.send(topicPaymentFailed, event.getTransactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PaymentFailed: transactionId={}", event.getTransactionId(), ex);
                }
            });
    }
}
