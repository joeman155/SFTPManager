package com.sftpmanager.controller;

import com.sftpmanager.model.AccountControls;
import com.sftpmanager.service.AccountControlsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/accountcontrols")
@CrossOrigin(origins = "*")
public class AccountControlsController {

    private final AccountControlsService service;

    public AccountControlsController(AccountControlsService service) {
        this.service = service;
    }


    @GetMapping
    public ResponseEntity<List<AccountControls>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountControls> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AccountControls> create(@RequestBody AccountControls entity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountControls> update(@PathVariable Long id, @RequestBody AccountControls entity) {
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
