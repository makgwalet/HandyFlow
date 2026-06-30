package za.co.handyflow.platform.security.dto;

import java.util.UUID;

/**
 * Codename-only view of a Principal — used anywhere a non-VIP_DETAIL_ACCESS
 * caller needs to reference a protection detail's principal (e.g. a general
 * shift-overview screen that happens to list CP engagements alongside site
 * shifts) without exposing the real name. Real identity is only available
 * via PrincipalResponse, which requires VIP_DETAIL_ACCESS.
 */
public record PrincipalSummaryResponse(
        UUID   id,
        String aliasCodename,
        String threatLevel
) {}
