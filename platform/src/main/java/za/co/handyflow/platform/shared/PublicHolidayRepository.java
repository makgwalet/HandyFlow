package za.co.handyflow.platform.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, UUID> {

    @Query("SELECT COUNT(h) > 0 FROM PublicHoliday h WHERE h.holidayDate = :date")
    boolean existsByDate(@Param("date") LocalDate date);

    /** Holiday dates within a range, inclusive. */
    @Query("SELECT h.holidayDate FROM PublicHoliday h WHERE h.holidayDate BETWEEN :from AND :to")
    List<LocalDate> datesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
