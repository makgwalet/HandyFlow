package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Stock items read from a CSV file by the screen. {@code dryRun} checks every row and creates nothing. */
public record StockImportRequest(
        @NotNull @Size(min = 1, max = 500) List<Row> rows,
        boolean dryRun
) {
    /** One line as typed; every value is text so a bad one becomes a row error. */
    public record Row(String itemName, String category, String unit, String quantity, String unitCost, String reorderLevel,
                      String supplier, String packSizeKg) {}
}
