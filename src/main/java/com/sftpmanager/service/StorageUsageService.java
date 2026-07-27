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
 * Reads per-service disk usage from sftp_service_usage — real byte counts
 * measured by XFS project quota accounting on the SFTP host and pushed back
 * by its sftp-quota-sync timer (~every minute; see PROFTPD-SETUP.md §8).
 * The app only ever READS this table; the host owns the writes.
 */
@Service
public class StorageUsageService {

    private static final Logger log = LoggerFactory.getLogger(StorageUsageService.class);

    private final JdbcTemplate jdbcTemplate;

    public StorageUsageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Bytes used per service id. Services the host hasn't reported yet
     * (brand new, nothing uploaded) come back as 0.
     */
    public Map<Long, Long> usedBytesByServiceIds(List<Long> serviceIds) {
        Map<Long, Long> out = new HashMap<>();
        for (Long id : serviceIds) out.put(id, 0L);
        if (serviceIds.isEmpty()) return out;

        // Ids come from our own DB, never user input
        String inList = serviceIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        try {
            jdbcTemplate.query(
                "SELECT sftp_service_id, used_bytes FROM sftp_service_usage "
                    + "WHERE sftp_service_id IN (" + inList + ")",
                rs -> {
                    out.put(rs.getLong("sftp_service_id"), Math.max(0, rs.getLong("used_bytes")));
                });
        } catch (Exception e) {
            // Usage table missing (fresh dev DB before first start) or DB
            // hiccup — usage display degrades to 0, never breaks the portal
            log.warn("Storage usage lookup failed: {}", e.getMessage());
        }
        return out;
    }
}
