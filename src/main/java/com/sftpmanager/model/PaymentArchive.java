package com.sftpmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Snapshot of a Payment (plus the owning user's identity at the time of
 * archiving), written when a User is deleted so financial records survive
 * independently of the user_id FK — no relation back to User on purpose,
 * since the whole point is this row must outlive that row.
 */
@Entity
@Table(name = "payments_arc")
public class PaymentArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_payment_id")
    private Long originalPaymentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email")
    private String email;

    @Column(name = "firstname")
    private String firstname;

    @Column(name = "surname")
    private String surname;

    @Column(name = "mobile_number")
    private String mobileNumber;

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

    @Column(name = "payment_created_at")
    private LocalDateTime paymentCreatedAt;

    @Column(name = "archived_at", updatable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onCreate() { archivedAt = LocalDateTime.now(); }

    public PaymentArchive() {}

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(Long v) { this.originalPaymentId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getFirstname() { return firstname; }
    public void setFirstname(String v) { this.firstname = v; }
    public String getSurname() { return surname; }
    public void setSurname(String v) { this.surname = v; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String v) { this.mobileNumber = v; }
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
    public LocalDateTime getPaymentCreatedAt() { return paymentCreatedAt; }
    public void setPaymentCreatedAt(LocalDateTime v) { this.paymentCreatedAt = v; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime v) { this.archivedAt = v; }
}
