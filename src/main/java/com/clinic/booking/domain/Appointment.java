package com.clinic.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private User patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    protected Appointment() {
    }

    public Appointment(Slot slot, User patient, String idempotencyKey, LocalDateTime createdAt) {
        this.slot = slot;
        this.patient = patient;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.status = AppointmentStatus.BOOKED;
    }

    public void cancel(LocalDateTime when) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = when;
    }

    public void markReminderSent() {
        this.reminderSent = true;
    }

    public Long getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public User getPatient() {
        return patient;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }
}
