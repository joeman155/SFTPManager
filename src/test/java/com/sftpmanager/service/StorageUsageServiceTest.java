package com.sftpmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageUsageServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private StorageUsageService service;

    @BeforeEach
    void setUp() {
        service = new StorageUsageService(jdbcTemplate);
    }

    /** Simulates sftp_service_usage returning rows of (sftp_service_id, used_bytes). */
    private void stubUsage(Map<Long, Long> rows) {
        doAnswer((InvocationOnMock inv) -> {
            RowCallbackHandler handler = inv.getArgument(1);
            for (var e : rows.entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("sftp_service_id")).thenReturn(e.getKey());
                when(rs.getLong("used_bytes")).thenReturn(e.getValue());
                handler.processRow(rs);
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));
    }

    @Test
    void emptyIdListNeverTouchesTheDatabase() {
        assertThat(service.usedBytesByServiceIds(List.of())).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void mapsUsageRowsToServiceIds() {
        stubUsage(Map.of(42L, 5_000_000L, 7L, 123L));

        Map<Long, Long> out = service.usedBytesByServiceIds(List.of(42L, 7L));

        assertThat(out).containsEntry(42L, 5_000_000L).containsEntry(7L, 123L);
    }

    @Test
    void servicesNotYetReportedByTheHostDefaultToZero() {
        stubUsage(Map.of(42L, 999L)); // no row for service 7

        Map<Long, Long> out = service.usedBytesByServiceIds(List.of(42L, 7L));

        assertThat(out).containsEntry(42L, 999L).containsEntry(7L, 0L);
    }

    @Test
    void negativeValuesAreClampedToZero() {
        stubUsage(Map.of(42L, -1024L));

        assertThat(service.usedBytesByServiceIds(List.of(42L))).containsEntry(42L, 0L);
    }

    @Test
    void queryFailureDegradesToZerosInsteadOfThrowing() {
        doThrow(new RuntimeException("relation sftp_service_usage does not exist"))
            .when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        Map<Long, Long> out = service.usedBytesByServiceIds(List.of(42L, 7L));

        assertThat(out).containsEntry(42L, 0L).containsEntry(7L, 0L);
    }

    @Test
    void queriesOnlyTheRequestedServiceIds() {
        stubUsage(Map.of());

        service.usedBytesByServiceIds(List.of(42L));

        verify(jdbcTemplate).query(contains("(42)"), any(RowCallbackHandler.class));
    }
}
