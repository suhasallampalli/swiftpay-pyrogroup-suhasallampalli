package com.swiftpay.gateway.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.controller.PaymentController;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.exception.GlobalExceptionHandler;
import com.swiftpay.gateway.model.PaymentStatus;
import com.swiftpay.gateway.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PaymentService paymentService;

    @Test
    @DisplayName("POST /v1/payments - 202 Accepted for valid request")
    void initiatePayment_valid_returns202() throws Exception {
        PaymentRequest request = buildRequest("txn-001", "user-001", "user-002", "100.00", "USD");
        PaymentResponse response = new PaymentResponse("txn-001", "user-001", "user-002",
            new BigDecimal("100.00"), "USD", PaymentStatus.PENDING, Instant.now());
        given(paymentService.initiatePayment(any())).willReturn(response);

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.transactionId").value("txn-001"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /v1/payments - 400 Bad Request when transaction_id missing")
    void initiatePayment_missingTransactionId_returns400() throws Exception {
        PaymentRequest request = buildRequest(null, "user-001", "user-002", "100.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /v1/payments - 400 Bad Request when amount is zero")
    void initiatePayment_zeroAmount_returns400() throws Exception {
        PaymentRequest request = buildRequest("txn-001", "user-001", "user-002", "0.00", "USD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/payments - 400 Bad Request when currency invalid length")
    void initiatePayment_invalidCurrency_returns400() throws Exception {
        PaymentRequest request = buildRequest("txn-001", "user-001", "user-002", "100.00", "USDD");

        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/payments/{id} - 200 OK for existing payment")
    void getPayment_found_returns200() throws Exception {
        PaymentResponse response = new PaymentResponse("txn-001", "user-001", "user-002",
            new BigDecimal("100.00"), "USD", PaymentStatus.COMPLETED, Instant.now());
        given(paymentService.getPayment("txn-001")).willReturn(response);

        mockMvc.perform(get("/v1/payments/txn-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /v1/payments/{id} - 404 Not Found for missing payment")
    void getPayment_notFound_returns404() throws Exception {
        given(paymentService.getPayment("txn-missing"))
            .willThrow(new EntityNotFoundException("Payment not found: txn-missing"));

        mockMvc.perform(get("/v1/payments/txn-missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    private PaymentRequest buildRequest(String txnId, String sender, String receiver,
                                        String amount, String currency) {
        PaymentRequest req = new PaymentRequest();
        req.setTransactionId(txnId);
        req.setSenderId(sender);
        req.setReceiverId(receiver);
        req.setAmount(amount != null ? new BigDecimal(amount) : null);
        req.setCurrency(currency);
        return req;
    }
}
