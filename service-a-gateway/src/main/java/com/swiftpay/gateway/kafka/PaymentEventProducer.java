package com.swiftpay.gateway.kafka;

import com.swiftpay.events.PaymentInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicPaymentInitiated;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                 @Value("${swiftpay.kafka.topics.payment-initiated}") String topicPaymentInitiated) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicPaymentInitiated = topicPaymentInitiated;
    }

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Publishing PaymentInitiated event for transactionId={}", event.getTransactionId());
        kafkaTemplate.send(topicPaymentInitiated, event.getTransactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PaymentInitiated for transactionId={}", event.getTransactionId(), ex);
                } else {
                    log.debug("Published PaymentInitiated for transactionId={} to partition={}",
                        event.getTransactionId(), result.getRecordMetadata().partition());
                }
            });
    }
}
