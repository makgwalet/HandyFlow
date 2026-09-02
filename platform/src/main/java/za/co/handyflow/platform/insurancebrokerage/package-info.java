/**
 * Insurance Brokerage — Increment 8b, the PROVIDER half of Module 8
 * (see {@code za.co.handyflow.platform.insurance} package-info for the
 * INTERNAL half, `insurance`, Increment 8a — a tenant's own asset-cover
 * tracker, built and delivered first).
 * <p>
 * WHY THIS IS A SEPARATE MODULE, NOT A MODE FLAG ON `insurance`: verified
 * against this codebase's own repeated real pattern for "does this
 * business function happen internally, or via an outsourced provider
 * serving external clients" — {@code training}/{@code trainingprovider}
 * and {@code facilitiesmanagement}/{@code facilities} are both two
 * separate, independently-subscribable {@code module_catalogue} entries,
 * never one module with an internal/provider switch. A tenant running
 * only `insurance` (their own asset cover) should never see client
 * management or commission-invoice screens they have no use for, and a
 * brokerage tenant subscribing to `insurancebrokerage` has no reason to
 * also carry `insurance`'s asset-tracker screens. Confirmed scope
 * decision from the prior session, restated here rather than re-derived.
 * <p>
 * ENTITY STYLE: this is a PROVIDER module — a business serving external
 * clients, not a tenant managing its own physical/operational state — so
 * it follows the plain-entity, raw-UUID provider-module convention this
 * specific family already established ({@code RecAgencyClient}/
 * {@code RecAgencyInvoice}, {@code PayClient}/{@code PayFeeNote},
 * {@code CollAgencyClient}/{@code CollAgencyCommissionInvoice}) —
 * {@code @Id UUID} assigned in the field initializer, a raw {@code UUID
 * tenant_id} column, manual {@code createdAt}/{@code updatedAt}, String
 * status fields rather than {@code @Enumerated} Java enums — confirmed
 * directly against {@code CollAgencyClient}/{@code CollAgencyCommissionInvoice}
 * source, NOT the {@code @Embedded TenantId}/{@code AggregateRoot}
 * convention `insurance` (internal) itself uses, because that convention
 * belongs to the OTHER family (`legalcompliance`/`debtcollection`/
 * `insurance` — a tenant operating on its own data).
 * <p>
 * COMMISSION / GL BOUNDARY — the central design decision of this module,
 * mirrored EXACTLY off {@code CollAgencyCommissionInvoice}/
 * {@code CollAgencyTrustTransactionService}: client premium is money that
 * was never the brokerage-tenant's own revenue or asset — it is
 * collected on behalf of, and owed to, the insurer. This module does
 * NOT model premium collection or a trust ledger at all (no
 * {@code CollAgencyTrustTransaction} equivalent exists here — confirmed
 * scope, see the module's own status doc §1). ONLY the brokerage's own
 * earned commission ({@code InsBrokCommissionInvoice}) ever posts to the
 * tenant's real chart of accounts, via {@code AccountingFacade}, using
 * the same {@code createJournalEntry()}/{@code postJournalEntry()}/
 * {@code getAccounts()} three-method pattern
 * {@code RecruitmentAgencyService}/{@code PayrollBureauService}/
 * {@code CollAgencyTrustTransactionService} (commission leg only)
 * already use — never a bespoke posting mechanism invented for this
 * module.
 * <p>
 * LIFECYCLE: {@code InsBrokPolicy} runs QUOTE -&gt; BOUND -&gt; ACTIVE
 * (new business) or, for a renewal, a new row is created directly in
 * ACTIVE (continuation of existing cover, no re-quoting) — same
 * "renewal creates a new chained row, never mutates the old one" shape
 * {@code InsPolicy} (internal) already uses via
 * {@code renewalOfPolicyId}. A single hook — {@code activate()} — is
 * where a commission invoice is generated, whether the policy reached
 * ACTIVE via the QUOTE/BOUND path or was created there directly by
 * {@code renew()}, so commission-generation logic exists in exactly one
 * place, not duplicated across two call paths.
 * <p>
 * REPOSITORY TENANT BIND: every {@code @Query} in this module binds a
 * raw {@code UUID tenantId} column directly (no {@code @Embedded
 * TenantId}, so the {@code :#{#tenantId.value}} SpEL-unwrap question
 * documented in {@code HandyFlow-Bug-EntitlementService-TenantModule-Disconnect.md}
 * does not even apply here — confirmed by checking
 * {@code CollAgencyClientRepository}'s own plain {@code :tenantId} bind
 * against a plain {@code UUID} field, not the embeddable).
 * <p>
 * allowedDependencies:
 *   shared        — TenantId/TenantSequenceService/etc.
 *   billing       — FeatureGuard.requireModule(), same as every other
 *                   separately-subscribable module.
 *   accounting    — AccountingFacade, for posting commission revenue
 *                   ONLY (createJournalEntry/postJournalEntry/getAccounts
 *                   — same three methods RecruitmentAgencyService/
 *                   PayrollBureauService/CollAgencyTrustTransactionService
 *                   already use for this exact purpose). Never used for
 *                   client premium — see above.
 *   evidence      — EvidenceFacade, for policy schedules, quote
 *                   documents, and other client/insurer correspondence.
 *   notifications — policy-expiring / policy-expired-unrenewed sweeps
 *                   and commission-invoice-overdue reminders, via
 *                   NotificationService, same pattern as every other
 *                   scheduler in this codebase.
 * <p>
 * Deliberately NOT a dependency: `insurance` (no dependency in either
 * direction — the platform's own SaaS principle: no module may assume
 * another module exists, and Tenant D may run one without the other) or
 * `identity` (this module's own client/insurer master data already
 * carries the display names it needs, same reason `collectionsagency`/
 * `warehousing` skipped `identity` too).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "accounting", "evidence", "notifications"}
)
package za.co.handyflow.platform.insurancebrokerage;
