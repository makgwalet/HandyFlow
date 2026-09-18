package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.TenantId;

/**
 * Renders a tenant's email sign-off as a ready-to-embed HTML fragment.
 * <p>
 * Deliberately returns a plain {@code String}, not a DTO/entity type:
 * {@code EmailTemplates} (shared) is a pure static utility class with zero
 * Spring/JPA dependencies today, by design — passing it a rendered HTML
 * fragment instead of a rich signature object keeps that property intact
 * rather than pulling identity-module types into shared.
 * <p>
 * Returns an empty string (never null) when the tenant has no signature
 * configured or has it disabled — callers can always safely concatenate
 * the result into an email body with no null-check and no visible change
 * for tenants who haven't opted in.
 */
public interface TenantEmailBrandingFacade {

    String renderSignatureHtml(TenantId tenantId);
}
