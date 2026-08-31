package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByTransactionId(String transactionId);

    @Query("SELECT t FROM Transaction t WHERE t.senderId = :userId OR t.receiverId = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findByUserId(@Param("userId") String userId);
}
