package com.seventhray.contracts.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "status_changes")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Enumerated(EnumType.STRING)
    private ContractStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt = Instant.now();

    protected StatusChange() {}

    public StatusChange(Contract contract, ContractStatus from, ContractStatus to) {
        this.contract = contract;
        this.fromStatus = from;
        this.toStatus = to;
    }

    public ContractStatus getFromStatus() { return fromStatus; }
    public ContractStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
}
