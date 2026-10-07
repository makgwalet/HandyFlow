package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.shared.TenantId;

/** What the tender looks like right now, as a fingerprint. An interface so the service can be tested without repositories. */
public interface PackageInputsProvider {
    PackageInputs current(TenantId tenantId, Tender tender);
}
