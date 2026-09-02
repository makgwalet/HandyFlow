/**
 * Bookkeeping Services — Module 6 of the 11-module Track 7 build-out. A
 * bookkeeping practice serving a portfolio of external client
 * businesses: per-client bank-feed import, transaction categorization
 * and reconciliation against a per-client chart of accounts and journal
 * (the same bank-import-to-GL workflow the tenant's own {@code
 * accounting} module already gives one tenant for itself, here rebuilt
 * per external client), monthly period close, staff time-logging for
 * time-and-materials clients, per-client retainer-or-time-and-materials
 * service agreements, real GL-posted invoicing back to the practice's
 * own tenant books, and a client portal.
 * <p>
 * SCOPE CONFIRMED AGAINST THE REAL REPO BEFORE ANY CODE WAS WRITTEN,
 * NO INTERNAL VARIANT — a genuine departure from every prior split
 * module (Debt Collection, Warehousing, Training, Facilities): the
 * tenant's own {@code accounting} module already IS a complete
 * bank-import → categorize → reconcile → GL pipeline for the tenant's
 * own books ({@code AccBankAccount}/{@code AccBankTransaction},
 * {@code importBankTransactions}, {@code getMatchCandidates},
 * {@code reconcileTransaction}/{@code reconcileWithNewJournal} — its own
 * Javadoc literally describes reconciliation as "categorizing" a bank
 * transaction). There is no separate in-house "bookkeeping department"
 * shape left to build — an SME doing its own books already has exactly
 * that in {@code accounting}. So unlike every prior module, this one
 * asks no internal-vs-provider sequencing question; it is provider-only.
 * <p>
 * WHY THIS ISN'T JUST {@code accountant}: {@code accountant} (an
 * existing, already-built practice-management module for firms serving
 * a client portfolio) names "BankRecon" and "FixedAssets" in its own
 * package-info's stated Layer 3 scope, but confirmed by direct source
 * read this session, NEITHER EXISTS ANYWHERE IN {@code accountant} — no
 * {@code AccBankAccount}/{@code AccBankTransaction}-equivalent, no
 * import, no reconciliation, at the client level. {@code accountant}
 * today is professional-services billing (time entries -> fee notes)
 * and SARS-compliance-deadline tracking bolted onto a bare manual GL
 * (staff type journal entries by hand; there is no data-ingestion
 * pipeline at all). This module is the dedicated build of that missing
 * piece — the actual bookkeeping work (import a client's bank
 * statement, categorize it, reconcile it, close the month) — rather
 * than an ad-hoc addition bolted onto {@code accountant}'s
 * billing-first shape. {@code auditor} was also checked and ruled out
 * as a template: it isn't a multi-client practice module at all, just a
 * single-tenant read-only evidence/control-exceptions portal for one
 * external auditor.
 * <p>
 * BILLING MODEL: confirmed with you up front — retainer OR
 * time-and-materials per client (not one or the other exclusively),
 * reusing the {@code FmServiceAgreement}/{@code coversDate()} pattern
 * just established for Module 5b ({@code facilitiesmanagement}), since
 * it fits how real bookkeeping practices price (flat monthly for
 * routine bookkeeping, hourly for cleanup/catch-up work) and is now a
 * proven shape in this codebase rather than a third billing pattern.
 * Time-and-materials clients get {@code BkTimeEntry} (staff logs hours
 * against a client), mirroring {@code accountant.TimeEntry} almost
 * exactly.
 * <p>
 * ENTITY CONVENTION: the modern {@code @Embedded TenantId} plain-entity
 * provider-module shape established by every module built THIS
 * engagement ({@code facilities}, {@code facilitiesmanagement},
 * {@code trainingprovider}, etc.) — NOT the older raw-{@code UUID
 * tenantId} shape {@code accounting}/{@code accountant} themselves still
 * use (those predate this engagement and are out of scope to change).
 * {@code BkBankAccount}/{@code BkBankTransaction} mirror {@code
 * AccBankAccount}/{@code AccBankTransaction}'s fields and behaviour
 * closely (same CSV-import shape, same duplicate-skip-not-error
 * semantics, same reconcile-against-existing-line vs.
 * reconcile-with-new-journal split) but are this module's own entities,
 * scoped additionally by {@code clientId} — not a reuse of {@code
 * accounting}'s tenant-owned tables, which belong to the practice's own
 * books, not any of its clients'.
 * <p>
 * allowedDependencies: {@code shared}, {@code identity} (branding portal
 * invite emails, matching every sibling provider module), {@code
 * billing} (FeatureGuard), {@code accounting} (AccountingFacade — the
 * practice's OWN revenue posting for invoices it raises against its
 * clients; this module never touches a client's books through {@code
 * accounting}, only its own — a client's books live entirely in this
 * module's own {@code Bk}-prefixed tables), {@code notifications}
 * (daily sweep — unreconciled transactions ageing, period not closed,
 * agreement expiring, invoice overdue).
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "billing", "accounting", "notifications"})
package za.co.handyflow.platform.bookkeeping;

import org.springframework.modulith.ApplicationModule;
