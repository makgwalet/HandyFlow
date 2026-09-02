/**
 * Training Provider — outsourced-provider variant (Module 4b). An
 * accredited training company / academy running courses for multiple
 * external client organizations: a course catalogue, scheduled
 * sessions (public/open-enrollment or closed/in-house to one client),
 * delegates nominated by those clients, enrollments, certifications,
 * and per-client billing.
 * <p>
 * WHY A SEPARATE MODULE, NOT A MODE ON `training` (Module 4a): the
 * same reasoning already established for every other internal/provider
 * pair in this codebase (`debtcollection`/`collectionsagency`,
 * `hr`/`payrollbureau`) — this module's clients are external
 * organizations that aren't necessarily HandyFlow tenants themselves,
 * and the provider needs its own client-portfolio and billing layer
 * that has no equivalent in single-tenant `training`. Confirmed by
 * direct source read: neither module imports the other, and this
 * module has NO dependency on `training` in either direction — a
 * delegate here is a completely separate concept from an employee
 * enrolled in Module 4a, not a shared entity or even a shared id.
 * <p>
 * WHY THE PLAIN-ENTITY PROVIDER-MODULE CONVENTION, NOT Module 4a's
 * HR-mirroring CONVENTION: 4a deliberately matched `HrEmployee`'s own
 * shape because it constantly references real `HrEmployee` records by
 * id. This module has no such relationship — its "employee" equivalent
 * (`TrainingProviderDelegate`) is an external contact nominated by a
 * client, the exact same shape `PayEmployee`'s own Javadoc describes
 * ("this employee doesn't work for the bureau's tenant, they work for
 * payClientId"). So this module follows the confirmed real
 * plain-entity provider-module family instead
 * (RecruitmentAgency/PayrollBureau/BookingAgency/Accountant/
 * CollectionsAgency/Warehousing): {@code @Entity} with no shared
 * superclass, {@code @Id private UUID id = UUID.randomUUID();},
 * raw UUID {@code tenant_id}, manual createdAt/updatedAt, String
 * status, {@code @Version Long version} (boxed, matching this family —
 * not Module 4a's {@code long} primitive).
 * <p>
 * `identity` for TenantFacade (branding reminder emails with the
 * provider's own tenant details, same as every sibling provider
 * module). `billing` for FeatureGuard — this is its own separately-
 * subscribable module. `accounting` for AccountingFacade — billing a
 * client for delegate training posts real revenue, unlike Module 4a
 * which deliberately does not. `evidence` for accreditation
 * certificates, sign-in sheets and materials. `notifications` for the
 * daily sweep (accreditation expiry, upcoming sessions, certificate
 * expiry, overdue invoices).
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "billing", "accounting", "evidence", "notifications"})
package za.co.handyflow.platform.trainingprovider;

import org.springframework.modulith.ApplicationModule;
