package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.List;

/** A report as a table of text. {@code numeric} columns are right-aligned by the screen and not guarded in the CSV. */
public record ReportResponse(String code, String title, String description, List<Column> columns, List<List<String>> rows,
                             long total, boolean truncated, Instant generatedAt, String note) {

    public record Column(String key, String label, boolean numeric) {}
}
