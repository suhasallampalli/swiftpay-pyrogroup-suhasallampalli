package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.dto.TransactionHistoryResponse;
import com.swiftpay.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Ledger", description = "Transaction history and ledger operations")
@RestController
@RequestMapping("/v1")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Operation(summary = "Get transaction history for a user")
    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<List<TransactionHistoryResponse>> getTransactionHistory(
            @PathVariable String userId) {
        return ResponseEntity.ok(ledgerService.getTransactionHistory(userId));
    }

    @Operation(summary = "Get a specific transaction by ID")
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<TransactionHistoryResponse> getTransaction(
            @PathVariable String transactionId) {
        return ResponseEntity.ok(ledgerService.getTransaction(transactionId));
    }
}
