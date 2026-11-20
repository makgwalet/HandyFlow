package za.co.handyflow.platform.legalcompliance.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Who hears about a campaign that is running late: the person who launched it, or the tenant's admins when that person cannot be found. A seam so it can be tested without identity. */
@FunctionalInterface
public interface CampaignOwners {

    List<Recipient> of(TenantId tenant, UUID launchedBy);

    @Component
    class ViaIdentity implements CampaignOwners {
        private final TenantAdminRecipients recipients;

        public ViaIdentity(TenantAdminRecipients recipients) { this.recipients = recipients; }

        @Override public List<Recipient> of(TenantId tenant, UUID launchedBy) {
            List<Recipient> owner = launchedBy == null ? List.of() : recipients.resolveUsers(tenant, List.of(launchedBy));
            return owner.isEmpty() ? recipients.resolveTenantAdmins(tenant) : owner;
        }
    }
}
