package com.swiftpay.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Payment request payload")
public class PaymentRequest {

    @Schema(description = "Unique idempotency key for this transaction", example = "txn-abc-123")
    @NotBlank(message = "transaction_id is required")
    private String transactionId;

    @Schema(description = "Sender user ID", example = "user-001")
    @NotBlank(message = "sender_id is required")
    private String senderId;

    @Schema(description = "Receiver user ID", example = "user-002")
    @NotBlank(message = "receiver_id is required")
    private String receiverId;

    @Schema(description = "Amount to transfer", example = "100.00")
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @Schema(description = "ISO 4217 currency code", example = "USD")
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be 3 characters")
    private String currency;

    public PaymentRequest() {}

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
}
