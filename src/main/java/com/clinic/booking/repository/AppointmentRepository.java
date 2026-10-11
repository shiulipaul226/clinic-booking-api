package com.clinic.booking.repository;

import com.clinic.booking.domain.Appointment;
import com.clinic.booking.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByPatientIdAndIdempotencyKey(Long patientId, String idempotencyKey);

    long countBySlotIdAndStatus(Long slotId, AppointmentStatus status);

    @Query("select a from Appointment a join fetch a.slot s join fetch s.doctor join fetch a.patient "
            + "where a.id = :id")
    Optional<Appointment> findDetailedById(@Param("id") Long id);

    @Query("select a from Appointment a join fetch a.slot s join fetch s.doctor join fetch a.patient "
            + "where a.patient.id = :patientId order by s.startTime")
    List<Appointment> findDetailedByPatient(@Param("patientId") Long patientId);

    @Query("select a from Appointment a join fetch a.slot s join fetch s.doctor join fetch a.patient "
            + "order by s.startTime")
    List<Appointment> findAllDetailed();

    @Query("select a from Appointment a join fetch a.slot s join fetch s.doctor join fetch a.patient "
            + "where a.status = com.clinic.booking.domain.AppointmentStatus.BOOKED "
            + "and a.reminderSent = false and s.startTime >= :from and s.startTime < :to")
    List<Appointment> findDueForReminder(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
