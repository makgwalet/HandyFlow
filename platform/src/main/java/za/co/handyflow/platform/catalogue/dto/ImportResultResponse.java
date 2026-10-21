package za.co.handyflow.platform.catalogue.dto;

import java.util.List;
import java.util.UUID;

/** What an applied import did. {@code problems} lists the rows that were left out or could not be saved. */
public record ImportResultResponse(UUID jobId, int total, int created, int updated, int unchanged, int skipped, int failed, List<String> problems) {}
