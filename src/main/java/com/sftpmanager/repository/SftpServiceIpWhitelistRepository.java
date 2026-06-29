package com.sftpmanager.repository;

import com.sftpmanager.model.SftpServiceIpWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SftpServiceIpWhitelistRepository extends JpaRepository<SftpServiceIpWhitelist, Long> {
    List<SftpServiceIpWhitelist> findBySftpServiceId(Long sftpServiceId);
}
