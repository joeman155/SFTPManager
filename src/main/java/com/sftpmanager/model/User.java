package com.sftpmanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "First name is required")
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank(message = "Surname is required")
    @Column(name = "surname", nullable = false)
    private String surname;

    @Column(name = "company")
    private String company;

    @Column(name = "company_size")
    private String companySize;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "state")
    private String state;

    @Pattern(regexp = "^[0-9A-Za-z\\s\\-]{3,10}$", message = "Invalid postcode format")
    @Column(name = "postcode")
    private String postcode;

    @Column(name = "country")
    private String country;

    @Pattern(regexp = "^[\\+]?[0-9\\s\\-\\(\\)]{7,20}$", message = "Invalid phone number")
    @Column(name = "phone")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "cc_number")
    private String ccNumber;

    @Column(name = "cc_name")
    private String ccName;

    @Column(name = "cc_expiry")
    private String ccExpiry;

    @Column(name = "backup_cc_number")
    private String backupCcNumber;

    @Column(name = "backup_cc_name")
    private String backupCcName;

    @Column(name = "backup_cc_expiry")
    private String backupCcExpiry;

    // Auth type: GOOGLE or EMAIL
    @Column(name = "auth_type")
    private String authType = "GOOGLE";

    @Column(name = "password_hash")
    private String passwordHash;

    // Role: 10 = admin, 1 = standard user
    @Column(name = "role", nullable = false)
    private Integer role = 1;

    // Onboarding
    @Column(name = "onboarded", nullable = false)
    private Boolean onboarded = false;

    @Column(name = "services_deactivated", nullable = false)
    private Boolean servicesDeactivated = false;

    @Column(name = "locked", nullable = false)
    private Boolean locked = false;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "trial_expires")
    private LocalDate trialExpires;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_controls_id")
    private AccountControls accountControls;

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

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getSurname() { return surname; }
    public void setSurname(String v) { this.surname = v; }
    public String getCompany() { return company; }
    public void setCompany(String v) { this.company = v; }
    public String getCompanySize() { return companySize; }
    public void setCompanySize(String v) { this.companySize = v; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String v) { this.addressLine1 = v; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String v) { this.addressLine2 = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getPostcode() { return postcode; }
    public void setPostcode(String v) { this.postcode = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getCcNumber() { return ccNumber; }
    public void setCcNumber(String v) { this.ccNumber = v; }
    public String getCcName() { return ccName; }
    public void setCcName(String v) { this.ccName = v; }
    public String getCcExpiry() { return ccExpiry; }
    public void setCcExpiry(String v) { this.ccExpiry = v; }
    public String getBackupCcNumber() { return backupCcNumber; }
    public void setBackupCcNumber(String v) { this.backupCcNumber = v; }
    public String getBackupCcName() { return backupCcName; }
    public void setBackupCcName(String v) { this.backupCcName = v; }
    public String getBackupCcExpiry() { return backupCcExpiry; }
    public void setBackupCcExpiry(String v) { this.backupCcExpiry = v; }
    public String getAuthType() { return authType; }
    public void setAuthType(String v) { this.authType = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public Integer getRole() { return role; }
    public void setRole(Integer v) { this.role = v; }
    public Boolean getOnboarded() { return onboarded; }
    public void setOnboarded(Boolean v) { this.onboarded = v; }
    public Boolean getServicesDeactivated() { return servicesDeactivated; }
    public void setServicesDeactivated(Boolean v) { this.servicesDeactivated = v; }
    public Boolean getLocked() { return locked; }
    public void setLocked(Boolean v) { this.locked = v; }
    public Integer getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(Integer v) { this.failedLoginAttempts = v; }
    public LocalDate getTrialExpires() { return trialExpires; }
    public void setTrialExpires(LocalDate v) { this.trialExpires = v; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan v) { this.plan = v; }
    public AccountControls getAccountControls() { return accountControls; }
    public void setAccountControls(AccountControls v) { this.accountControls = v; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime v) { this.creationDate = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public LocalDateTime getLastUpdatedDate() { return lastUpdatedDate; }
    public void setLastUpdatedDate(LocalDateTime v) { this.lastUpdatedDate = v; }
    public String getLastUpdatedBy() { return lastUpdatedBy; }
    public void setLastUpdatedBy(String v) { this.lastUpdatedBy = v; }
}
