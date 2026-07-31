package com.sftpmanager.repository;

import com.sftpmanager.model.PaymentArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentArchiveRepository extends JpaRepository<PaymentArchive, Long> {
}
