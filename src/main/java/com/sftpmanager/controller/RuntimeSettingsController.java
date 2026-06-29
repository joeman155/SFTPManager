package com.sftpmanager.controller;

import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.service.RuntimeSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/runtimesettings")
@CrossOrigin(origins = "*")
public class RuntimeSettingsController {

    private final RuntimeSettingsService service;

    public RuntimeSettingsController(RuntimeSettingsService service) {
        this.service = service;
    }


    @GetMapping
    public ResponseEntity<List<RuntimeSettings>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuntimeSettings> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RuntimeSettings> create(@RequestBody RuntimeSettings entity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuntimeSettings> update(@PathVariable Long id, @RequestBody RuntimeSettings entity) {
        try {
            return ResponseEntity.ok(service.update(id, entity));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
