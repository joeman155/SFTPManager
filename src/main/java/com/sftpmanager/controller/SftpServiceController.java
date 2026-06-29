package com.sftpmanager.controller;

import com.sftpmanager.model.SftpService;
import com.sftpmanager.service.SftpServiceService;
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
@RequestMapping("/api/sftpservices")
@CrossOrigin(origins = "*")
public class SftpServiceController {

    private final SftpServiceService service;

    public SftpServiceController(SftpServiceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SftpService>> getAll() {
        return ResponseEntity.ok(service.findAll());
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
    public ResponseEntity<?> create(@Valid @RequestBody SftpService sftpService,
                                    @RequestParam(required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(sftpService, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody SftpService sftpService,
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
