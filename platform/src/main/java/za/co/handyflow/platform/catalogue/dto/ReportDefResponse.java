package za.co.handyflow.platform.catalogue.dto;

/** A report in the list. {@code available} is false when it needs price access the caller does not have. */
public record ReportDefResponse(String code, String group, String title, String description, boolean needsPrices, boolean available) {}
