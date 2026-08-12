package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * SA public holidays — genuinely national reference data, same
 * "shared, not duplicated" reasoning as SarsTaxTable/SarsTaxRebate (see
 * those classes' Javadoc). Maps to acc_public_holidays, which already
 * exists and is already correctly populated — accountant.DeadlineEngine
 * currently reads it via raw JdbcTemplate rather than a registered JPA
 * entity, since nothing needed a typed entity for it before now.
 * <p>
 * WHY THIS IS A PROPER SHARED ENTITY, NOT A RAW SQL REACH: this
 * engagement already flagged reading another module's table by raw SQL
 * as the exact anti-pattern that caused real bugs (Expenses->Accounting,
 * Section 15.1). Rather than have payrollbureau's new deadline engine
 * copy that same shortcut against acc_public_holidays' table name, this
 * gives it a properly declared, shared-owned entity instead — the
 * correct fix, not a second instance of the problem. accountant's own
 * DeadlineEngine is left using its existing raw JDBC for now (it
 * already works, not broken, not touched by this change) — but any
 * NEW code needing public holiday data should use this instead of
 * copying DeadlineEngine's raw-SQL approach.
 */
@Entity
@Table(name = "acc_public_holidays")
@Getter
@NoArgsConstructor
public class PublicHoliday {

    @Id
    private UUID id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;
}