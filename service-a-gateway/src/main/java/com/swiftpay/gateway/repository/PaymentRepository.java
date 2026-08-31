package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByTransactionId(String transactionId);
}
