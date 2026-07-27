package com.sftpmanager.service;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.repository.AccountControlsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControlsServiceTest {

    @Mock private AccountControlsRepository repository;

    private AccountControlsService service;

    @BeforeEach
    void setUp() {
        service = new AccountControlsService(repository);
    }

    @Test
    void updateCopiesAllPlanFields() {
        AccountControls existing = new AccountControls();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(AccountControls.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountControls updated = new AccountControls();
        updated.setPlan("Pro");
        updated.setDescription("Pro plan");
        updated.setMonthlyPriceCents(4900L);
        updated.setMaxUsers(25);
        updated.setMaxServers(10);
        updated.setMaxStorageMb(51200L);
        updated.setTrialDays(null);
        updated.setLastUpdatedBy("admin");

        AccountControls result = service.update(1L, updated);

        assertThat(result.getPlan()).isEqualTo("Pro");
        assertThat(result.getDescription()).isEqualTo("Pro plan");
        assertThat(result.getMonthlyPriceCents()).isEqualTo(4900L);
        assertThat(result.getMaxUsers()).isEqualTo(25);
        assertThat(result.getMaxServers()).isEqualTo(10);
        assertThat(result.getMaxStorageMb()).isEqualTo(51200L);
        assertThat(result.getTrialDays()).isNull();
        assertThat(result.getLastUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateThrowsWhenPlanNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new AccountControls()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found: 99");
    }

    @Test
    void findAllUsesTheStableBusinessOrdering() {
        // Plain findAll() returns rows in Postgres physical order, which
        // changes when a row is edited — listings must use findAllOrdered()
        service.findAll();

        verify(repository).findAllOrdered();
        verify(repository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void saveAndDeleteDelegateToRepository() {
        AccountControls plan = new AccountControls();
        when(repository.save(plan)).thenReturn(plan);

        assertThat(service.save(plan)).isEqualTo(plan);

        service.deleteById(2L);
        verify(repository).deleteById(2L);
    }
}
