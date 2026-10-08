package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.util.List;

/** Paged claims list and its summary figures (patch 0178). */
public final class ClaimListDtos {

    private ClaimListDtos() {}

    /** One page of claims; {@code page} is zero-based. */
    public record ClaimPage(List<ClinicClaimResponse> content, long total, int page, int size) {}

    /** Figures over every claim of the status, not just the page on screen. */
    public record ClaimSummary(long total, BigDecimal outstanding, BigDecimal paid, long rejected) {}
}
