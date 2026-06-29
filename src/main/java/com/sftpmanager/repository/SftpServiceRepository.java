package com.sftpmanager.repository;

import com.sftpmanager.model.SftpService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SftpServiceRepository extends JpaRepository<SftpService, Long> {
    List<SftpService> findByUserId(Long userId);
}
