package za.co.handyflow.platform.shared;

import java.util.List;

/**
 * How a module tells Legal Compliance about its own compliance position without either module depending on the other.
 *
 * A module implements this as a Spring bean in its own package; Legal Compliance collects every implementation it finds. Neither side imports the other, so
 * Spring Modulith's module rules are unaffected. Rules for implementers:
 *  - read-only, tenant-scoped, fast (it runs on a page view): use counts, never load everything;
 *  - counts only (see {@link ComplianceSignal}); no personal information, because the reader may not have access to the source module;
 *  - return only signals above zero;
 *  - the module still owns its data and its screens; the signal's actionUrl points back to them.
 */
public interface ComplianceSignalSource {

    /** The module's billing key, for example "fleet". Legal Compliance only asks a tenant's modules it actually has. */
    String moduleKey();

    /** The module's name as shown to people, for example "Fleet". */
    String moduleName();

    List<ComplianceSignal> signals(TenantId tenantId);
}
