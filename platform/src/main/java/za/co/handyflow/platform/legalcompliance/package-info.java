/**
 * Legal / Compliance (internal) — "manage our own legal exposure," not
 * "run a law firm" (that is Legal Practice, a separate future
 * outsourced-provider module). The user is an in-house legal counsel or
 * compliance officer.
 * <p>
 * MVP scope built here: regulatory obligation tracker, litigation/dispute
 * register, org-wide POPIA processing-activity register with DSAR
 * tracking. Contract repository/lifecycle is deliberately NOT rebuilt
 * here — `contracting` already owns that (entity, e-signature, renewal
 * reminders) and is the proven, working system of record. This module
 * depends on `contracting`'s new ContractingFacade (read-only) so a
 * regulatory obligation or litigation matter can optionally reference a
 * specific contract, and so the obligation calendar can surface contract
 * renewals alongside regulatory deadlines — without duplicating contract
 * data or lifecycle logic. See ContractingFacade's own Javadoc for the
 * full reasoning.
 * <p>
 * allowedDependencies:
 *   shared        — TenantId/AggregateRoot/TenantSequenceService/etc., as every module needs.
 *   billing       — FeatureGuard.requireModule(), same as every other gated module.
 *   evidence      — EvidenceFacade, for litigation-matter documents and POPIA/regulatory evidence
 *                   (court papers, signed policies, DSAR correspondence) — never a bespoke
 *                   base64 attachment table, matching this engagement's standing rule.
 *   notifications — regulatory-deadline and DSAR-due-date alerts via NotificationService,
 *                   same pattern as every other proactive-expiry scheduler in this codebase
 *                   (Fleet/Security/Contracting).
 *   contracting   — ContractingFacade only (read-only contract summaries), per the design
 *                   decision above.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "evidence", "notifications", "contracting"}
)
package za.co.handyflow.platform.legalcompliance;
