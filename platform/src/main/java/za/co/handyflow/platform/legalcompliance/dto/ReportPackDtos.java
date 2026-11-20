package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Board pack request: the reports to include (in this order), an optional title, and an optional date for the reports that can be run as at a date. */
public final class ReportPackDtos {

    private ReportPackDtos() {}

    public record PackRequest(@NotNull @Size(min = 1, max = 20) List<String> keys, @Size(max = 100) String title, @Size(max = 10) String asOf) {}
}
