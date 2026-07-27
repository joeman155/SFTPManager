package com.sftpmanager.controller;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.service.SftpServiceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sftpservices")
@CrossOrigin(origins = "*")
public class SftpServiceController {

    private final SftpServiceService service;
    private final com.sftpmanager.service.StorageUsageService storageUsageService;

    public SftpServiceController(SftpServiceService service,
                                 com.sftpmanager.service.StorageUsageService storageUsageService) {
        this.service = service;
        this.storageUsageService = storageUsageService;
    }

    @GetMapping
    public ResponseEntity<List<SftpService>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * Storage per service for the admin UI: real used bytes (measured by the
     * SFTP host's XFS quota accounting) and the limit from the owner's plan.
     * Response: { "<serviceId>": { "usedBytes": n, "limitBytes": n|null } }
     */
    @GetMapping("/storage")
    public ResponseEntity<Map<Long, Map<String, Object>>> storage() {
        List<SftpService> services = service.findAll();
        Map<Long, Long> used = storageUsageService.usedBytesByServiceIds(
            services.stream().map(SftpService::getId).toList());

        Map<Long, Map<String, Object>> out = new HashMap<>();
        for (SftpService s : services) {
            Long limitMb = s.getUser() != null && s.getUser().getAccountControls() != null
                ? s.getUser().getAccountControls().getMaxStorageMb() : null;
            Map<String, Object> entry = new HashMap<>();
            entry.put("usedBytes", used.getOrDefault(s.getId(), 0L));
            entry.put("limitBytes", limitMb != null ? limitMb * 1048576L : null);
            out.put(s.getId(), entry);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/byuser/{userId}")
    public ResponseEntity<List<SftpService>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(service.findByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SftpService> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SftpService sftpService,
                                    @RequestParam(required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(sftpService, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody SftpService sftpService,
                                    @RequestParam(required = false) Long userId) {
        try {
            return ResponseEntity.ok(service.update(id, sftpService, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errors);
    }
}
