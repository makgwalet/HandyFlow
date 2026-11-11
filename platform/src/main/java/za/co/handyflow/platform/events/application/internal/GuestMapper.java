package za.co.handyflow.platform.events.application.internal;

import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.dto.GuestResponse;

import java.util.Map;
import java.util.UUID;

/** Single place that turns an EventGuest into its API shape, shared by every service in this package. */
final class GuestMapper {

    private GuestMapper() {}

    /** Tier name comes from a pre-loaded map — no extra DB query per guest. */
    static GuestResponse toResponse(EventGuest g, Map<UUID, String> tierNames) {
        String tierName = g.getTierId() != null ? tierNames.getOrDefault(g.getTierId(), "—") : "—";
        return new GuestResponse(g.getId(), g.getTicketNumber(), g.getQrCode(),
                g.getFullName(), g.getEmail(), g.getPhone(), g.getCompany(),
                g.getDietaryRequirements(), g.getTierId(), tierName,
                g.getStatus(), g.getPaymentStatus(), g.getAmountPaid(),
                g.getCheckedInAt(), g.getCreatedAt(), g.getSource(), g.getGroupRef());
    }
}