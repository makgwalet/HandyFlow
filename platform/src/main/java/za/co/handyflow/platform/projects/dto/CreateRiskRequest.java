package za.co.handyflow.platform.projects.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateRiskRequest(
        String      title,          // required
        String      description,
        String      category,       // SAFETY|FINANCIAL|SCHEDULE|TECHNICAL|LEGAL|ENVIRONMENTAL
        int         probability,    // 1–5
        int         impact,         // 1–5
        String      mitigation,
        UUID        ownerId,
        String      ownerName,
        LocalDate   reviewDate,
        boolean     isOhsa
) {}
