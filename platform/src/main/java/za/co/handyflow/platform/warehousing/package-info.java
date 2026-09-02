/**
 * Warehousing — outsourced-provider module for a **third-party logistics
 * (3PL) / public warehousing operator**: a business that stores, receives,
 * and fulfills inventory on behalf of multiple external client businesses,
 * billing them for storage and handling. This is Module 3 of the 11-module
 * strategic plan ("Warehouse/Inventory").
 * <p>
 * WHY A SEPARATE MODULE, NOT AN EXTENSION OF `supplychain`: confirmed by
 * direct source reading before writing a line of this module —
 * `za.co.handyflow.platform.supplychain` already implements a working
 * INTERNAL multi-location inventory system for a single tenant's own
 * business (ScStockLocation, ScInventory with on-hand/reserved/in-transit
 * qty, reorder points, weighted-average costing, bin location, lot/expiry
 * tracking, ScStockMovement ledger, purchase orders, goods-received notes,
 * 3-way-matched supplier invoices, low-stock alerts). That already
 * satisfies "a business manages its own stock." This module is a
 * genuinely different business model — the same distinction the strategic
 * plan draws everywhere else (hr/payrollbureau, accounting/accountant,
 * recruiter/recruitmentagency, debtcollection/collectionsagency): a 3PL
 * operator's clients are not necessarily HandyFlow tenants themselves, and
 * the operator needs its own client-portfolio/storage-billing layer that
 * has no equivalent in `supplychain`. This module does NOT depend on
 * `supplychain` and does NOT import any of its classes — confirmed by
 * design, not an oversight. (User-confirmed scope decision: this module IS
 * the 3PL/public-warehousing outsourced-provider variant; `supplychain`
 * remains the internal-inventory answer and is left untouched.)
 * <p>
 * CORE OPERATIONAL LOOP (per this engagement's own "never duplicate
 * information, information should flow" philosophy):
 * Client onboarding -&gt; client's item/SKU catalogue (WhseItem) -&gt;
 * inbound shipment/ASN (WhseInboundShipment -&gt; receiving increases
 * WhseInventory) -&gt; storage (WhseInventory sits in a WhseLocation) -&gt;
 * outbound order (WhseOutboundOrder -&gt; pick/pack/ship decreases
 * WhseInventory) -&gt; periodic client billing (WhseBillingInvoice: storage
 * fee + handling fee, the ONLY thing that posts to the tenant's real GL —
 * see WhseBillingService's own Javadoc for the billing model and the
 * simplification it deliberately flags rather than over-building).
 * <p>
 * NOT a trust-accounting module like `collectionsagency`: unlike a debt
 * collector, this operator isn't holding a client's MONEY — it's holding a
 * client's GOODS and charging an ordinary service fee for doing so. There
 * is no analogous "client's money vs. the tenant's own revenue" split, so
 * this module posts its billing invoices straight to the real chart of
 * accounts via AccountingFacade, the same createJournalEntry()/
 * postJournalEntry() pattern RecruitmentAgencyService/PayrollBureauService/
 * CollAgencyTrustTransactionService (commission leg only) already use.
 * <p>
 * ENTITY STYLE: follows the plain-entity, raw-UUID provider-module
 * convention this specific family already established (RecAgencyClient,
 * PayClient, CollAgencyClient — @Id UUID assigned in the field initializer,
 * a raw UUID tenant_id column, manual createdAt/updatedAt, String status
 * fields rather than @Enumerated Java enums), confirmed directly against
 * those classes, not assumed from `supplychain`'s own (different) entity
 * style.
 * <p>
 * allowedDependencies:
 *   shared        — TenantId/TenantSequenceService/etc.
 *   billing       — FeatureGuard.requireModule(), same as every other
 *                   separately-subscribable module.
 *   accounting    — AccountingFacade, for posting storage+handling revenue
 *                   on invoice issue (same three methods every provider
 *                   module already uses this for).
 *   evidence      — EvidenceFacade, for proof-of-delivery, packing slips,
 *                   ASN/inbound-shipment supporting documents.
 *   notifications — inbound shipments overdue to receive, outbound orders
 *                   overdue to ship, and billing-invoice overdue reminders,
 *                   via NotificationService, same pattern as every other
 *                   scheduler in this codebase.
 * <p>
 * Deliberately NOT a dependency: `supplychain` (see above — no dependency
 * in either direction) or `identity` (this module's own WhseProfile
 * already carries the warehouse operator's own display name, same reason
 * `collectionsagency` skipped `identity` too).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "accounting", "evidence", "notifications"}
)
package za.co.handyflow.platform.warehousing;
