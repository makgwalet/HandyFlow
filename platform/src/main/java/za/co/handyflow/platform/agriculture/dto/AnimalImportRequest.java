package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Animals read from a CSV file by the screen. {@code dryRun} checks every row and creates nothing. */
public record AnimalImportRequest(
        @NotNull @Size(min = 1, max = 500) List<Row> rows,
        boolean dryRun
) {
    /** One line as typed; every value is text so a bad one becomes a row error. */
    public record Row(String tagNumber, String name, String species, String sex, String breed, String dateOfBirth,
                      String area, String acquisitionType, String acquisitionDate) {}
}
