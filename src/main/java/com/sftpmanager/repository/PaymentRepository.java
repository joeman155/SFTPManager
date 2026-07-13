package com.sftpmanager.repository;

import com.sftpmanager.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Payment> findTop100ByOrderByCreatedAtDesc();
    long countByCreatedAtAfter(LocalDateTime after);
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
    boolean existsByGatewayPaymentId(String gatewayPaymentId);
}
