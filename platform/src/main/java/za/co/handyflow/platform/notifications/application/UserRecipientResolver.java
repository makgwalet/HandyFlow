package za.co.handyflow.platform.notifications.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface UserRecipientResolver {
    Optional<Recipient> resolveUser(TenantId tenantId, UUID userId);
}