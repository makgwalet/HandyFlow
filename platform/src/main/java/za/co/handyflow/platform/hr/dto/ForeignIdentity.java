package za.co.handyflow.platform.hr.dto;

import java.time.LocalDate;

/**
 * Who a person is when they have no South African ID, and their right to work. The block is read and written whole: sending it
 * replaces all six values, so clearing a field clears it.
 */
public record ForeignIdentity(String passportNumber, String passportCountry, LocalDate passportExpiry,
                              String workPermitType, String workPermitNumber, LocalDate workPermitExpiry) {

    public static final ForeignIdentity NONE = new ForeignIdentity(null, null, null, null, null, null);
}
