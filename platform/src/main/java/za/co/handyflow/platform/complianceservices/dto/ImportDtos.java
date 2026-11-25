package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {

    private ImportDtos() {}

    public record DiscardRequest(@NotBlank @Size(max = 4000) String note) {}

    public record BatchView(UUID id, String kind, String fileName, String status, int totalRows, int okRows, int duplicateRows, int rejectedRows, int importedRows,
                            UUID createdBy, Instant createdAt, UUID committedBy, Instant committedAt, String discardNote) {}

    public record RowView(int rowNumber, List<String> fields, String status, String message, UUID targetId) {}

    /** columns names what each position in a row's fields means. */
    public record BatchDetail(BatchView batch, List<String> columns, List<RowView> rows, boolean rowsTruncated) {}
}
