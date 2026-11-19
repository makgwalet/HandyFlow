package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The people an attestation campaign can be sent to. A port, so the module does not depend on the identity module's internals:
 * the default implementation (JdbcUserDirectory) reads active users directly and can be replaced by a facade in the identity module.
 */
public interface UserDirectory {

    record Person(UUID id, String name, String email) {}

    /** Active users of the tenant, optionally only those in the given department (exact, case-insensitive). */
    List<Person> activeUsers(TenantId tenantId, String department);
}
