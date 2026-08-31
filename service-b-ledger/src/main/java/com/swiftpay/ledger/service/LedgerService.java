package com.swiftpay.ledger.service;

import com.swiftpay.events.PaymentCompletedEvent;
import com.swiftpay.events.PaymentFailedEvent;
import com.swiftpay.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.dto.TransactionHistoryResponse;
import com.swiftpay.ledger.kafka.PaymentEventProducer;
import com.swiftpay.ledger.model.Account;
import com.swiftpay.ledger.model.Transaction;
import com.swiftpay.ledger.model.TransactionStatus;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentEventProducer paymentEventProducer;

    public LedgerService(AccountRepository accountRepository,
                         TransactionRepository transactionRepository,
                         PaymentEventProducer paymentEventProducer) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.paymentEventProducer = paymentEventProducer;
    }

    @Transactional
    public void processPayment(PaymentInitiatedEvent event) {
        String transactionId = event.getTransactionId();

        if (transactionRepository.findByTransactionId(transactionId).isPresent()) {
            log.warn("Transaction already processed: transactionId={}", transactionId);
            return;
        }

        Transaction transaction = new Transaction(
            transactionId,
            event.getSenderId(),
            event.getReceiverId(),
            event.getAmount(),
            event.getCurrency(),
            TransactionStatus.PENDING
        );
        transactionRepository.save(transaction);

        Account sender = accountRepository.findByUserIdForUpdate(event.getSenderId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Sender account not found: " + event.getSenderId()));

        if (sender.getBalance().compareTo(event.getAmount()) < 0) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Insufficient funds");
            transactionRepository.save(transaction);

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                transactionId, event.getSenderId(), event.getReceiverId(),
                event.getAmount(), event.getCurrency(), "Insufficient funds", Instant.now());
            paymentEventProducer.publishPaymentFailed(failedEvent);

            log.warn("Payment failed - insufficient funds: transactionId={}, sender={}, balance={}, amount={}",
                transactionId, event.getSenderId(), sender.getBalance(), event.getAmount());
            return;
        }

        Account receiver = accountRepository.findByUserIdForUpdate(event.getReceiverId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Receiver account not found: " + event.getReceiverId()));

        sender.setBalance(sender.getBalance().subtract(event.getAmount()));
        receiver.setBalance(receiver.getBalance().add(event.getAmount()));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);

        PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(
            transactionId, event.getSenderId(), event.getReceiverId(),
            event.getAmount(), event.getCurrency(), Instant.now());
        paymentEventProducer.publishPaymentCompleted(completedEvent);

        log.info("Payment completed: transactionId={}", transactionId);
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getTransactionHistory(String userId) {
        return transactionRepository.findByUserId(userId).stream()
            .map(t -> new TransactionHistoryResponse(
                t.getTransactionId(), t.getSenderId(), t.getReceiverId(),
                t.getAmount(), t.getCurrency(), t.getStatus(),
                t.getFailureReason(), t.getCreatedAt(), t.getUpdatedAt()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransaction(String transactionId) {
        Transaction t = transactionRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Transaction not found: " + transactionId));
        return new TransactionHistoryResponse(
            t.getTransactionId(), t.getSenderId(), t.getReceiverId(),
            t.getAmount(), t.getCurrency(), t.getStatus(),
            t.getFailureReason(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
