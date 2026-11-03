package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Public client-portal payload.
 * CHUNK 6: documents — approved, CLIENT-visible register entries (metadata only, no file URL).
 * CHUNK 2: budgetTotal REMOVED, and the nested lists now use dedicated client-safe
 * DTOs instead of the internal TaskResponse / SnagResponse / RiskResponse.
 */
public record ProjectPortalResponse(
        String      projectNumber,
        String      name,
        String      clientName,
        String      status,
        String      health,
        LocalDate   startDate,
        LocalDate   endDate,
        BigDecimal  completionPct,
        List<PortalMilestoneResponse> milestones,
        List<PortalSnagResponse>      openSnags,
        List<PortalRiskResponse>      redRisks,
        List<PortalDocumentResponse>  documents
) {}