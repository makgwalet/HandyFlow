package za.co.handyflow.platform.fleet.dto;

import za.co.handyflow.platform.fleet.domain.model.ComplianceStatus;

import java.util.List;
import java.util.UUID;

public record VehicleComplianceResponse(
        UUID vehicleId,
        String registration,
        ComplianceStatus overallStatus,     // the worst status among the items
        List<ComplianceItemResponse> items
) {}