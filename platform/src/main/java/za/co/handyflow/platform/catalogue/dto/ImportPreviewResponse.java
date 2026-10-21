package za.co.handyflow.platform.catalogue.dto;

import java.util.List;

/** The dry run: what the import would do, row by row. Rows with problems come first. {@code truncated} when more rows exist than are listed. */
public record ImportPreviewResponse(String mode, int total, long create, long update, long unchanged, long errors,
                                    List<String> fileProblems, List<String> unknownColumns, List<RowResult> rows, boolean truncated) {

    public record RowResult(int line, String key, String action, List<String> changes, List<String> errors, List<String> notes) {}
}
