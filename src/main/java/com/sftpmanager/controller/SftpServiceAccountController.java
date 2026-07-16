package com.sftpmanager.controller;

import com.sftpmanager.model.SftpServiceAccount;
import com.sftpmanager.service.SftpServiceAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sftpserviceaccounts")
@CrossOrigin(origins = "*")
public class SftpServiceAccountController {

    private final SftpServiceAccountService service;

    public SftpServiceAccountController(SftpServiceAccountService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SftpServiceAccount>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/byservice/{sftpServiceId}")
    public ResponseEntity<List<SftpServiceAccount>> getByService(@PathVariable Long sftpServiceId) {
        return ResponseEntity.ok(service.findBySftpServiceId(sftpServiceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SftpServiceAccount> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SftpServiceAccount account,
                                    @RequestParam(required = false) Long sftpServiceId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.save(account, sftpServiceId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody SftpServiceAccount account,
                                    @RequestParam(required = false) Long sftpServiceId) {
        try {
            return ResponseEntity.ok(service.update(id, account, sftpServiceId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
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
