package com.sftpmanager.repository;

import com.sftpmanager.model.AccountControls;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountControlsRepository extends JpaRepository<AccountControls, Long> {
    Optional<AccountControls> findByPlan(String plan);

    /**
     * Plans in stable business order: free/trial first (null or 0 price),
     * then by ascending price (FREE → BASIC → ENTERPRISE), id as tiebreak.
     * Postgres returns findAll() rows in physical order, which CHANGES when
     * a row is updated — every plan listing must use this instead.
     */
    @Query("SELECT a FROM AccountControls a ORDER BY a.monthlyPriceCents ASC NULLS FIRST, a.id ASC")
    List<AccountControls> findAllOrdered();
}
