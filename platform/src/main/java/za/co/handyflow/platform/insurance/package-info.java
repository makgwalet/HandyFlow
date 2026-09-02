/**
 * Insurance (Internal) — a tenant's own tracker for insurance it holds on
 * its OWN business assets (vehicles, property, equipment, general
 * liability) with a third-party insurer/broker. This is the INTERNAL
 * counterpart of Module 8; the PROVIDER counterpart — a brokerage placing
 * and managing policies on behalf of external clients, with commission
 * tracking — is a separate module, {@code insurancebrokerage}, built as
 * its own increment (matches the established internal/provider split
 * already used by {@code training}/{@code trainingprovider} and
 * {@code facilitiesmanagement}/{@code facilities}: two independently
 * subscribable modules, not one module with a mode flag).
 * <p>
 * SCOPE FOR THIS INCREMENT (confirmed via AskUserQuestion before any code
 * was written — see the status doc for the full record):
 * <ul>
 *   <li>Short-term lines only for this pass: MOTOR, PROPERTY, EQUIPMENT,
 *   LIABILITY, OTHER. Life/investment cover is out of scope here (that
 *   distinction matters more for a brokerage's product range than for a
 *   tenant tracking its own handful of asset policies, so it isn't
 *   modelled as a separate axis on {@code InsPolicy} at all).</li>
 *   <li>Policy lifecycle only — no claims management (FNOL, claim
 *   tracking, settlement) in this increment, deferred exactly as scoped.</li>
 *   <li>No hard link to {@code fleet}/{@code earthmoving} for the insured
 *   asset. Those packages are excluded from this session's own read
 *   access, so no existing asset-insurance fields could be checked there
 *   either way — but even if they existed, a hard FK would violate this
 *   platform's own SaaS principle that no module may assume another
 *   module exists (Tenant D could run Insurance with no Fleet module
 *   installed at all). {@code assetType}/{@code assetReference} are
 *   plain, freeform fields instead — the same graceful-degradation
 *   choice {@code AgAnimal}/{@code AgMovementRecordsTab} already made for
 *   their own out-of-module references.</li>
 * </ul>
 * <p>
 * ENTITY STYLE: {@code @Embedded TenantId} + {@code @AttributeOverride},
 * matching {@code LpMatter}/{@code AgAnimal}/{@code FmProfile} — the
 * dominant convention across every module built since Agriculture,
 * not the older raw-UUID {@code RecAgencyClient}/{@code CollAgencyProfile}
 * family. Every {@code @Query} binds {@code tenantId} DIRECTLY
 * ({@code WHERE x.tenantId = :tenantId}), never via the
 * {@code :#{#tenantId.value}} SpEL unwrap — that form is reserved for
 * entities whose tenant column really is a raw {@code UUID} (e.g.
 * {@code ModuleSubscription}/{@code TenantModule}), and using it against
 * an {@code @Embedded TenantId} field is the exact bug documented in
 * {@code HandyFlow-Bug-EntitlementService-TenantModule-Disconnect.md}
 * (confirmed still present, unfixed, in {@code facilitiesmanagement},
 * {@code training}, {@code facilities}, {@code trainingprovider} as of
 * that audit) — this module is written to avoid it from day one.
 * <p>
 * allowedDependencies:
 *   shared        — TenantId, standard.
 *   billing       — FeatureGuard.requireModule("insurance"), same as
 *                   every other separately-subscribable module.
 *   notifications — daily expiring/expired-policy sweep.
 * <p>
 * Deliberately EXCLUDED from this increment: {@code accounting} — no
 * GL-posting need was confirmed for a tenant's own insurance expense
 * (it's just a cost the tenant already tracks however it tracks any
 * other expense/accounts-payable line); same Phase-1 judgment Agriculture
 * made for its own {@code accounting} dependency. Revisit only if a real
 * premium-payment/GL-posting need is confirmed. {@code evidence} — policy
 * document/schedule attachment is a real, plausible follow-up but wasn't
 * part of the confirmed scope for this increment; flagged, not silently
 * dropped. {@code hr}/{@code fleet}/{@code earthmoving} — no employee or
 * asset-registry linkage in this increment, for the reasons above.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "notifications"}
)
package za.co.handyflow.platform.insurance;
