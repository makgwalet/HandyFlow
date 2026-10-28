package za.co.handyflow.platform.recruiter.dto;

import java.time.LocalDate;
import java.util.List;

public final class RecruiterReportDtos {
    private RecruiterReportDtos() {}

    public record ReportInfo(String id, String title, String definition, boolean usesDateRange) {}

    /** Every report is a table with its own definition, so the screen and the export read the same. */
    public record ReportTable(String id, String title, String definition, LocalDate from, LocalDate to, List<String> columns, List<List<Object>> rows) {}
}
