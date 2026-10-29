package com.eparly.api.savings.action.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "savings_action_history")
public class SavingsActionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private SavingsAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ActionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ActionStatus newStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "details", length = 1000)
    private String details;

    public SavingsActionHistory() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SavingsAction getAction() { return action; }
    public void setAction(SavingsAction action) { this.action = action; }

    public ActionStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(ActionStatus previousStatus) { this.previousStatus = previousStatus; }

    public ActionStatus getNewStatus() { return newStatus; }
    public void setNewStatus(ActionStatus newStatus) { this.newStatus = newStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavingsActionHistory h)) return false;
        return id != null && Objects.equals(id, h.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
