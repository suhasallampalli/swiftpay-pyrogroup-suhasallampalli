package com.swiftpay.events;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentInitiatedEvent {

    private String transactionId;
    private String senderId;
    private String receiverId;
    private BigDecimal amount;
    private String currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    public PaymentInitiatedEvent() {}

    public PaymentInitiatedEvent(String transactionId, String senderId, String receiverId,
                                  BigDecimal amount, String currency, Instant timestamp) {
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
