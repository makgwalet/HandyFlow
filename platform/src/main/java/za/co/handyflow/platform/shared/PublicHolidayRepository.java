package za.co.handyflow.platform.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, UUID> {

    @Query("SELECT COUNT(h) > 0 FROM PublicHoliday h WHERE h.holidayDate = :date")
    boolean existsByDate(@Param("date") LocalDate date);
}