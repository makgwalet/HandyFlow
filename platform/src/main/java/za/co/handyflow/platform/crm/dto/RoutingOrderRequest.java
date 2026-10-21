package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

/** Every rule id, in the order they should be tried. */
public record RoutingOrderRequest(List<UUID> ids) {}
