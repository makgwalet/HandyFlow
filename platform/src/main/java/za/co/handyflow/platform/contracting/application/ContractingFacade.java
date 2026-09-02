package za.co.handyflow.platform.contracting.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ContractingFacade — the public API of the `contracting` module.
 *
 * NEW: added specifically so `legalcompliance` (Legal/Compliance internal
 * module) can read contract data without depending on ContractingService,
 * Contract, or ContractRepository directly — same anti-corruption-boundary
 * reasoning as CrmFacade/EvidenceFacade/ApprovalFacade (see any of those
 * for the fuller rationale on why cross-module access always goes through
 * a narrow facade interface, never a module's internal package).
 * <p>
 * WHY THIS EXISTS NOW, NOT EARLIER: `contracting` previously had zero
 * consumers outside its own module — HR triggers contract creation via an
 * event listener (ContractingHrEventHandler), not a facade call, so no
 * inbound-facing contract existed yet. Legal/Compliance is the first
 * module that needs to READ contract data from outside `contracting`
 * (to show contracts alongside regulatory obligations and litigation
 * matters on one compliance calendar, and to let a litigation matter or
 * regulatory obligation optionally reference a specific contract) —
 * this facade is scoped to exactly that need, nothing more.
 * <p>
 * DELIBERATELY READ-ONLY: Legal/Compliance's MVP scope (regulatory
 * tracker, litigation register, POPIA register) never needs to create,
 * edit, or transition a contract's lifecycle — `contracting` remains the
 * sole owner of contract lifecycle/e-signature/renewal-reminder logic.
 * If a future piece of work needs Legal/Compliance to originate a
 * contract (e.g. auto-drafting an NDA from a litigation matter), that's
 * a new, deliberate facade method added then — not assumed here.
 * <p>
 * WHY NO counterparty/party name on ContractSummary: Contract has no
 * first-class notion of "the other party" vs. "us" — ContractParty rows
 * are a flat, order-independent list (see Contract.java's own Javadoc on
 * why `parties` is @Transient and must be explicitly loaded). Guessing
 * which party is the more useful summary caption would need business
 * logic that doesn't exist yet on the entity itself. Callers that need a
 * specific contract's parties should follow up with a party-listing
 * facade method once that need is real, rather than this facade
 * approximating a "counterparty" field that could be wrong for any
 * multi-party (e.g. subcontractor + surety) contract.
 */
public interface ContractingFacade {

    /**
     * All non-deleted contracts for the tenant — used to populate a
     * "link this to an existing contract" picker on a Legal/Compliance
     * regulatory obligation or litigation matter.
     */
    List<ContractSummary> listAll(TenantId tenantId);

    /** A single contract by id, for display once linked. Empty if not found, deleted, or wrong tenant. */
    Optional<ContractSummary> findById(TenantId tenantId, UUID contractId);

    /**
     * SIGNED contracts whose endDate falls within the given number of
     * days from today (inclusive) — feeds Legal/Compliance's obligation
     * calendar view so contract renewals show up alongside regulatory
     * deadlines and litigation key dates in one place, without
     * Legal/Compliance re-deriving `contracting`'s own expiry-window
     * query logic (mirrors ContractRepository.findSignedExpiringWithin,
     * which ContractExpiryScheduler already uses for the identical
     * purpose inside `contracting` itself).
     */
    List<ContractSummary> listExpiringWithin(TenantId tenantId, int days);
}
