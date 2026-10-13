package za.co.handyflow.platform.debtcollection.domain.model;

import java.time.LocalDate;

public enum PtpFrequency {
    ONE_OFF, WEEKLY, FORTNIGHTLY, MONTHLY;

    /** Next calendar due date in a recurring series (anchored to the previous due date, not to today). */
    public LocalDate next(LocalDate from) {
        return switch (this) {
            case ONE_OFF -> throw new IllegalStateException("ONE_OFF has no next occurrence");
            case WEEKLY -> from.plusWeeks(1);
            case FORTNIGHTLY -> from.plusWeeks(2);
            case MONTHLY -> from.plusMonths(1);
        };
    }
}
