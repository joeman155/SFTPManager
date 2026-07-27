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

    /** Simulates the tally table returning rows of (name, bytes_in_used). */
    private void stubTallies(Map<String, Long> rows) {
        doAnswer((InvocationOnMock inv) -> {
            RowCallbackHandler handler = inv.getArgument(1);
            for (var e : rows.entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("name")).thenReturn(e.getKey());
                when(rs.getLong("bytes_in_used")).thenReturn(e.getValue());
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
    void mapsTallyRowsBackToServiceIds() {
        stubTallies(Map.of("svc42", 5_000_000L, "svc7", 123L));

        Map<Long, Long> out = service.usedBytesByServiceIds(List.of(42L, 7L));

        assertThat(out).containsEntry(42L, 5_000_000L).containsEntry(7L, 123L);
    }

    @Test
    void servicesWithoutATallyDefaultToZero() {
        stubTallies(Map.of("svc42", 999L)); // no row for service 7

        Map<Long, Long> out = service.usedBytesByServiceIds(List.of(42L, 7L));

        assertThat(out).containsEntry(42L, 999L).containsEntry(7L, 0L);
    }

    @Test
    void negativeTalliesAreClampedToZero() {
        // mod_quotatab can briefly go negative after out-of-band deletes
        stubTallies(Map.of("svc42", -1024L));

        assertThat(service.usedBytesByServiceIds(List.of(42L))).containsEntry(42L, 0L);
    }

    @Test
    void queryFailureDegradesToZerosInsteadOfThrowing() {
        doThrow(new RuntimeException("relation proftpd_quota_tallies does not exist"))
            .when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        Map<Long, Long> out = service.usedBytesByServiceIds(List.of(42L, 7L));

        assertThat(out).containsEntry(42L, 0L).containsEntry(7L, 0L);
    }

    @Test
    void queriesOnlyTheRequestedGroupNames() {
        stubTallies(Map.of());

        service.usedBytesByServiceIds(List.of(42L));

        org.mockito.Mockito.verify(jdbcTemplate)
            .query(contains("'svc42'"), any(RowCallbackHandler.class));
    }
}
