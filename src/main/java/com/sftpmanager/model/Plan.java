package com.sftpmanager.model;

import jakarta.persistence.*;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Monthly price in cents (e.g. 2900 = $29.00). Null/0 = free, never billed.
    @Column(name = "monthly_price_cents")
    private Long monthlyPriceCents;

    public Plan() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Long getMonthlyPriceCents() { return monthlyPriceCents; }
    public void setMonthlyPriceCents(Long v) { this.monthlyPriceCents = v; }
}
