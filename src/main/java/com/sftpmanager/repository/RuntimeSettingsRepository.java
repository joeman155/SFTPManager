package com.sftpmanager.repository;

import com.sftpmanager.model.RuntimeSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RuntimeSettingsRepository extends JpaRepository<RuntimeSettings, Long> {
    Optional<RuntimeSettings> findByName(String name);
    List<RuntimeSettings> findAllByOrderByNameAsc();
}
