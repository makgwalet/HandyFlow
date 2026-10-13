package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;

/** Letters only; status must be DELIVERED or RETURNED. detail e.g. "Uncollected at branch, returned to sender". */
public record UpdateDeliveryStatusRequest(@NotNull CommunicationStatus status, String detail) {}
