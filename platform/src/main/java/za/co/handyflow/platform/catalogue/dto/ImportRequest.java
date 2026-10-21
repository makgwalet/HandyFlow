package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.catalogue.domain.model.ImportRules;

/** The text of a CSV file (read in the browser), how to treat existing items, and whether rows with problems may be left out. */
public record ImportRequest(String fileName, @NotBlank(message = "Choose a file") String csv, @NotNull(message = "Choose what the import may do") ImportRules.Mode mode, Boolean skipErrors) {}
