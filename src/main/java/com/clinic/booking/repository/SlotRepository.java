package com.clinic.booking.repository;

import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByDoctorIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
            Long doctorId, LocalDateTime from, LocalDateTime to);

    List<Slot> findByDoctorIdAndStatusAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
            Long doctorId, SlotStatus status, LocalDateTime from, LocalDateTime to);

    boolean existsByDoctorIdAndStartTime(Long doctorId, LocalDateTime startTime);

    /**
     * The heart of the double-booking protection: an atomic compare-and-set done by the database.
     *
     * <p>"UPDATE ... WHERE status = AVAILABLE" can only succeed for ONE transaction; every concurrent
     * caller gets 0 affected rows and is turned into a 409 Conflict by the service layer.
     * The status is only changed through these queries (never via entity setters), so a stale
     * first-level cache in the same transaction is harmless.
     *
     * @return 1 if this call claimed the slot, 0 if it was already booked (or does not exist)
     */
    @Modifying(flushAutomatically = true)
    @Query("update Slot s set s.status = com.clinic.booking.domain.SlotStatus.BOOKED, s.version = s.version + 1 "
            + "where s.id = :id and s.status = com.clinic.booking.domain.SlotStatus.AVAILABLE")
    int markBooked(@Param("id") Long id);

    /** Releases a booked slot (used by cancel / reschedule). Returns 1 if it was released. */
    @Modifying(flushAutomatically = true)
    @Query("update Slot s set s.status = com.clinic.booking.domain.SlotStatus.AVAILABLE, s.version = s.version + 1 "
            + "where s.id = :id and s.status = com.clinic.booking.domain.SlotStatus.BOOKED")
    int markAvailable(@Param("id") Long id);
}
