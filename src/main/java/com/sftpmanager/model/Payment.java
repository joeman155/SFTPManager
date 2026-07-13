package com.sftpmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", nullable = false)
    private String currency;

    // SUCCEEDED or FAILED
    @Column(name = "status", nullable = false)
    private String status;

    // PRIMARY or BACKUP
    @Column(name = "card_used")
    private String cardUsed;

    @Column(name = "card_display")
    private String cardDisplay;

    @Column(name = "description")
    private String description;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "failure_reason")
    private String failureReason;

    // ADMIN:<email> or SCHEDULER
    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Payment() {}

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long v) { this.amountCents = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getCardUsed() { return cardUsed; }
    public void setCardUsed(String v) { this.cardUsed = v; }
    public String getCardDisplay() { return cardDisplay; }
    public void setCardDisplay(String v) { this.cardDisplay = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String v) { this.gatewayPaymentId = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String v) { this.initiatedBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
