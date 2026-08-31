package com.swiftpay.ledger.unit;

import com.swiftpay.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.kafka.PaymentEventProducer;
import com.swiftpay.ledger.model.Account;
import com.swiftpay.ledger.model.Transaction;
import com.swiftpay.ledger.model.TransactionStatus;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import com.swiftpay.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentEventProducer paymentEventProducer;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(accountRepository, transactionRepository, paymentEventProducer);
    }

    @Test
    @DisplayName("processPayment - successful debit/credit and emits COMPLETED event")
    void processPayment_success_debitsAndCredits() {
        PaymentInitiatedEvent event = buildEvent("txn-001", "user-001", "user-002", "100.00");
        given(transactionRepository.findByTransactionId("txn-001")).willReturn(Optional.empty());

        Account sender = new Account("user-001", new BigDecimal("500.00"), "USD");
        Account receiver = new Account("user-002", new BigDecimal("200.00"), "USD");
        given(accountRepository.findByUserIdForUpdate("user-001")).willReturn(Optional.of(sender));
        given(accountRepository.findByUserIdForUpdate("user-002")).willReturn(Optional.of(receiver));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(accountRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ledgerService.processPayment(event);

        assertThat(sender.getBalance()).isEqualByComparingTo("400.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("300.00");

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(1)).save(txnCaptor.capture());
        Transaction finalTxn = txnCaptor.getAllValues().stream()
            .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
            .findFirst().orElseThrow();
        assertThat(finalTxn.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(paymentEventProducer).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("processPayment - insufficient funds emits FAILED event and does not debit")
    void processPayment_insufficientFunds_emitsFailedEvent() {
        PaymentInitiatedEvent event = buildEvent("txn-002", "user-poor", "user-002", "1000.00");
        given(transactionRepository.findByTransactionId("txn-002")).willReturn(Optional.empty());

        Account sender = new Account("user-poor", new BigDecimal("10.00"), "USD");
        given(accountRepository.findByUserIdForUpdate("user-poor")).willReturn(Optional.of(sender));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ledgerService.processPayment(event);

        assertThat(sender.getBalance()).isEqualByComparingTo("10.00"); // unchanged
        verify(paymentEventProducer, never()).publishPaymentCompleted(any());
        verify(paymentEventProducer).publishPaymentFailed(any());

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(1)).save(txnCaptor.capture());
        assertThat(txnCaptor.getAllValues()).anySatisfy(t ->
            assertThat(t.getStatus()).isEqualTo(TransactionStatus.FAILED));
    }

    @Test
    @DisplayName("processPayment - duplicate event is skipped (idempotent)")
    void processPayment_duplicate_skipped() {
        PaymentInitiatedEvent event = buildEvent("txn-dup", "user-001", "user-002", "50.00");
        Transaction existing = new Transaction("txn-dup", "user-001", "user-002",
            new BigDecimal("50.00"), "USD", TransactionStatus.COMPLETED);
        given(transactionRepository.findByTransactionId("txn-dup")).willReturn(Optional.of(existing));

        ledgerService.processPayment(event);

        verify(accountRepository, never()).findByUserIdForUpdate(anyString());
        verify(paymentEventProducer, never()).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("processPayment - unknown sender throws exception")
    void processPayment_unknownSender_throws() {
        PaymentInitiatedEvent event = buildEvent("txn-003", "user-ghost", "user-002", "50.00");
        given(transactionRepository.findByTransactionId("txn-003")).willReturn(Optional.empty());
        given(accountRepository.findByUserIdForUpdate("user-ghost")).willReturn(Optional.empty());
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> ledgerService.processPayment(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("user-ghost");
    }

    private PaymentInitiatedEvent buildEvent(String txnId, String senderId, String receiverId, String amount) {
        return new PaymentInitiatedEvent(txnId, senderId, receiverId,
            new BigDecimal(amount), "USD", Instant.now());
    }
}
