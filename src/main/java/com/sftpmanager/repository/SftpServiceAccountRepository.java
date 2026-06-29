package com.sftpmanager.repository;

import com.sftpmanager.model.SftpServiceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SftpServiceAccountRepository extends JpaRepository<SftpServiceAccount, Long> {
    List<SftpServiceAccount> findBySftpServiceId(Long sftpServiceId);
}
