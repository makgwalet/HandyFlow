package za.co.handyflow.platform.crm.dto;

import java.util.UUID;

/**
 * FIX: backlog 4.1 — "no lead ownership/assignment" gap.
 * <p>
 * ownerId is deliberately NOT @NotNull — null is a valid, meaningful value
 * here ("unassign this customer, make it unowned again"), the same way
 * Customer.assignOwner() itself treats null as a legitimate target, not an
 * error to reject at the validation layer.
 */
public record UpdateOwnerRequest(UUID ownerId) {}