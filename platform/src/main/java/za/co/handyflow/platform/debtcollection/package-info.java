/**
 * Debt Collection (internal) — a business formally escalating collection of
 * its OWN unpaid invoices, where it is the original creditor. This is
 * deliberately narrower than a "debt collection" module in the generic
 * sense: it is Part B of the strategic plan (an internal department
 * capability), not the outsourced-provider variant. That variant —
 * Collections Agency, a registered third-party debt collector managing
 * portfolios for external creditor clients, with commission billing, a
 * trust/collection account, and Debt Collectors Act / stricter-NCA
 * compliance obligations that simply do not apply to an original creditor
 * — is being built separately as its own top-level module (not depending on
 * this one), mirroring this codebase's existing internal/provider module
 * pairs (hr/payrollbureau, accounting/accountant, recruiter/
 * recruitmentagency). See the Debt Collection status doc for the full
 * reasoning behind the split.
 * <p>
 * Relationship to what already exists: `invoicing` already has a soft,
 * automatic reminder mechanism (InvoicingScheduler.detectOverdueInvoices(),
 * escalating email reminders up to 5 thresholds) and `accounting` already
 * has AR-aging alerts (AccountingNotificationScheduler.sendOverdueArAlerts()).
 * This module does NOT duplicate either. It is the layer above them: a
 * formal, staff-managed CASE — opened deliberately by a person once soft
 * reminders have failed — with a structured contact log (compliance trail:
 * who contacted the debtor, when, how, and what was agreed), payment-plan
 * tracking, and a path to write-off or legal handover. A case references
 * one or more of the debtor's outstanding invoices by id; it does not copy
 * or re-own invoice/payment data, which stays owned by `invoicing`.
 * <p>
 * A case is scoped to a DEBTOR (customer), not to a single invoice — real
 * collections work targets the relationship (a debtor may owe on several
 * overdue invoices at once) and a payment plan or negotiated settlement
 * naturally covers the whole balance, not one invoice line. Because
 * `invoicing`'s own Invoice.customerId is nullable (walk-in clients), this
 * module's debtor identity is similarly optional-CRM-linked: customerId is
 * nullable, and debtor name/email/phone are captured as a snapshot on the
 * case itself so a case can exist for a walk-in debtor with no CRM record.
 * <p>
 * allowedDependencies:
 *   shared        — TenantId/AggregateRoot/TenantSequenceService/etc., as every module needs.
 *   billing       — FeatureGuard.requireModule(), same as every other gated module.
 *   invoicing     — InvoicingFacade (read-only): findOutstandingInvoices() to identify and
 *                   display what a case's debtor actually owes. This module does not write
 *                   to invoicing — recording an actual payment stays invoicing's job, this
 *                   module tracks the human collection effort around that debt.
 *   crm           — CrmFacade (read-only where possible, one write path): findCustomerById()
 *                   for debtor contact details when customerId is set, and
 *                   logCommunication() so a debt-collection contact also appears in the
 *                   customer's ordinary CRM activity timeline (this module's own
 *                   CollectionContactLog is the structured, debt-specific record — richer
 *                   fields, immutable compliance trail — CRM's log is the general-purpose
 *                   timeline every other module already feeds).
 *   evidence      — EvidenceFacade, for demand letters, payment-plan agreements, and other
 *                   case correspondence — never a bespoke attachment table.
 *   notifications — payment-plan-due and case-follow-up alerts via NotificationService, same
 *                   pattern as every other proactive scheduler in this codebase.
 * <p>
 * Deliberately NOT a dependency: `contracting`. A case may reference an
 * Acknowledgment-of-Debt contract by id (`linkedContractId`), created
 * manually by staff via the existing `contracting` module and linked here —
 * exactly the same "store the id, don't pull in the facade for one field"
 * choice legalcompliance's own RegulatoryObligation.linkedContractId /
 * LitigationMatter.linkedContractId already made (see LitigationMatterService
 * .linkContract() — it only stores the UUID, it does not call
 * ContractingFacade to validate it). Auto-generating the AOD from this
 * module is a real future opportunity (the ECTA-compliant "Acknowledgment
 * of Debt" template already exists in ContractTemplateSeeder) but
 * `ContractingFacade` is currently read-only and was not extended for this,
 * so it's flagged as a follow-up, not built speculatively into MVP.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "invoicing", "crm", "evidence", "notifications"}
)
package za.co.handyflow.platform.debtcollection;
