package com.sftpmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_controls")
public class AccountControls {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan", nullable = false)
    private String plan;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Monthly price in cents (e.g. 2900 = $29.00). Null/0 = free, never billed.
    @Column(name = "monthly_price_cents")
    private Long monthlyPriceCents;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_servers")
    private Integer maxServers;

    // Non-null = this plan is a time-limited trial of that many days
    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "last_updated_date")
    private LocalDateTime lastUpdatedDate;

    @Column(name = "last_updated_by")
    private String lastUpdatedBy;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastUpdatedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedDate = LocalDateTime.now();
    }

    public AccountControls() {}

    public AccountControls(Long id, String plan, Integer maxUsers, Integer maxServers, LocalDateTime creationDate, String createdBy, LocalDateTime lastUpdatedDate, String lastUpdatedBy) {
        this.id = id;
        this.plan = plan;
        this.maxUsers = maxUsers;
        this.maxServers = maxServers;
        this.creationDate = creationDate;
        this.createdBy = createdBy;
        this.lastUpdatedDate = lastUpdatedDate;
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    public Long getMonthlyPriceCents() {
        return monthlyPriceCents;
    }

    public void setMonthlyPriceCents(Long monthlyPriceCents) {
        this.monthlyPriceCents = monthlyPriceCents;
    }
    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }
    public Integer getMaxServers() {
        return maxServers;
    }

    public void setMaxServers(Integer maxServers) {
        this.maxServers = maxServers;
    }
    public Integer getTrialDays() {
        return trialDays;
    }

    public void setTrialDays(Integer trialDays) {
        this.trialDays = trialDays;
    }
    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    public LocalDateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }
}