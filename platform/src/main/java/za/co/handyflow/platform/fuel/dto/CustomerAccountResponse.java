package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerAccountResponse(UUID id, UUID crmCustomerId, String customerName, String customerType,
                                      BigDecimal creditLimit, int paymentTermsDays, boolean creditHold,
                                      String creditHoldReason, String licenceNumber, String notes,
                                      int siteCount, List<CustomerSiteResponse> sites, Instant createdAt) {}
