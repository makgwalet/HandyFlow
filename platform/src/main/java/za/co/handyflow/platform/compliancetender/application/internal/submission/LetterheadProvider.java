package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.shared.TenantId;

/** Builds the letterhead for one tender from the tenant's own details. Behind an interface so it can be replaced by a Business Profile later. */
public interface LetterheadProvider {

    Letterhead forTender(TenantId tenantId, Tender tender);
}
