package com.sftpmanager.repository;

import com.sftpmanager.model.AccountControls;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AccountControlsRepository extends JpaRepository<AccountControls, Long> {
    Optional<AccountControls> findByPlan(String plan);
}
