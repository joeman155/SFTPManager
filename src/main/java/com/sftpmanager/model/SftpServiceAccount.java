package com.sftpmanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sftp_service_account")
public class SftpServiceAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Authentication type is required")
    @Column(name = "authentication_type")
    private String authenticationType;

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Username must be alphanumeric with no spaces")
    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "enabled")
    private Boolean enabled = true;

    // Permissions stored as comma-separated: READ,WRITE,DELETE
    @Column(name = "permissions")
    private String permissions;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sftp_service_id", nullable = false)
    private SftpService sftpService;

    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "last_updated_date")
    private LocalDateTime lastUpdatedDate;

    @Column(name = "last_updated_by")
    private String lastUpdatedBy;

    @PrePersist
    protected void onCreate() { creationDate = LocalDateTime.now(); lastUpdatedDate = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { lastUpdatedDate = LocalDateTime.now(); }

    public SftpServiceAccount() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAuthenticationType() { return authenticationType; }
    public void setAuthenticationType(String v) { this.authenticationType = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String v) { this.publicKey = v; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String v) { this.permissions = v; }
    public SftpService getSftpService() { return sftpService; }
    public void setSftpService(SftpService v) { this.sftpService = v; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime v) { this.creationDate = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public LocalDateTime getLastUpdatedDate() { return lastUpdatedDate; }
    public void setLastUpdatedDate(LocalDateTime v) { this.lastUpdatedDate = v; }
    public String getLastUpdatedBy() { return lastUpdatedBy; }
    public void setLastUpdatedBy(String v) { this.lastUpdatedBy = v; }
}
