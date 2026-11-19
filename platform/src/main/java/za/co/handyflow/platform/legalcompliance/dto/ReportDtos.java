package za.co.handyflow.platform.legalcompliance.dto;

import java.time.Instant;
import java.util.List;

/** Standard reports. Cells are display strings so the screen and the CSV show exactly the same thing. */
public final class ReportDtos {

    private ReportDtos() {}

    /** containsNames: the report lists staff or owner names. Personal data of the public (requesters, data subjects, declarants) is never included in any report. */
    public record ReportInfo(String key, String category, String title, String description, boolean containsNames, boolean supportsAsOf) {}

    public record ReportResult(String key, String title, String category, Instant generatedAt, String generatedBy, String asOf, List<String> columns, List<List<String>> rows, int rowCount, boolean truncated) {}
}
