package com.swiftpay.analytics.kafka;

import com.swiftpay.analytics.service.AnalyticsService;
import com.swiftpay.events.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedConsumer.class);

    private final AnalyticsService analyticsService;

    public PaymentCompletedConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        autoCreateTopics = "true"
    )
    @KafkaListener(
        topics = "${swiftpay.kafka.topics.payment-completed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompleted event: transactionId={}", event.getTransactionId());
        analyticsService.recordPaymentCompleted(event);
    }
}
