package com.sftpmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_users")
public class PortalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_email", nullable = false, unique = true)
    private String googleEmail;

    @Column(name = "google_name")
    private String googleName;

    @Column(name = "google_picture")
    private String googlePicture;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() { createdDate = LocalDateTime.now(); lastLogin = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { lastLogin = LocalDateTime.now(); }

    public PortalUser() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGoogleEmail() { return googleEmail; }
    public void setGoogleEmail(String v) { this.googleEmail = v; }
    public String getGoogleName() { return googleName; }
    public void setGoogleName(String v) { this.googleName = v; }
    public String getGooglePicture() { return googlePicture; }
    public void setGooglePicture(String v) { this.googlePicture = v; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime v) { this.createdDate = v; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime v) { this.lastLogin = v; }
}
