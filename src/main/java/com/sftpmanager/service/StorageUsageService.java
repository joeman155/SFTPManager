package com.sftpmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads per-service disk usage from proftpd_quota_tallies — the table
 * ProFTPD's mod_quotatab keeps up to date (recalculated from actual disk
 * usage at every login via QuotaOptions ScanOnLogin, tracked live during
 * sessions). The app only ever READS this table; ProFTPD owns the writes.
 */
@Service
public class StorageUsageService {

    private static final Logger log = LoggerFactory.getLogger(StorageUsageService.class);

    private final JdbcTemplate jdbcTemplate;

    public StorageUsageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Bytes used per service id. Services with no tally yet (nothing ever
     * uploaded) come back as 0.
     */
    public Map<Long, Long> usedBytesByServiceIds(List<Long> serviceIds) {
        Map<Long, Long> out = new HashMap<>();
        for (Long id : serviceIds) out.put(id, 0L);
        if (serviceIds.isEmpty()) return out;

        // Group names are 'svc<id>' — ids come from our own DB, never user input
        String inList = serviceIds.stream()
            .map(id -> "'svc" + id + "'")
            .collect(Collectors.joining(","));
        try {
            jdbcTemplate.query(
                "SELECT name, bytes_in_used FROM proftpd_quota_tallies "
                    + "WHERE quota_type = 'group' AND name IN (" + inList + ")",
                rs -> {
                    long id = Long.parseLong(rs.getString("name").substring(3));
                    out.put(id, Math.max(0, rs.getLong("bytes_in_used")));
                });
        } catch (Exception e) {
            // Tally table missing (fresh dev DB before first start) or DB
            // hiccup — usage display degrades to 0, never breaks the portal
            log.warn("Storage usage lookup failed: {}", e.getMessage());
        }
        return out;
    }
}
