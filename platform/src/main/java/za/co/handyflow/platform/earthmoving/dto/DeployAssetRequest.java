package za.co.handyflow.platform.earthmoving.dto;

import java.time.LocalDate;

/** Give a siteId (a saved site) or a siteName (typed). A chosen site's customer and project are used unless others are given. */
public record DeployAssetRequest(
        String siteName,
        String clientName,
        String contactName,
        String contactPhone,
        LocalDate startDate,
        LocalDate expectedEndDate,
        String notes,
        java.util.UUID siteId,
        java.util.UUID customerId,
        java.util.UUID projectId
) {
    public DeployAssetRequest(String siteName, String clientName, String contactName, String contactPhone,
                              LocalDate startDate, LocalDate expectedEndDate, String notes) {
        this(siteName, clientName, contactName, contactPhone, startDate, expectedEndDate, notes, null, null, null);
    }
}