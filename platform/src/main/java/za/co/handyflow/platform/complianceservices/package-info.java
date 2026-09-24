/**
 * complianceservices — "Compliance Services": the external-service-
 * provider layer on top of {@code compliancetender}, for a tenant
 * managing compliance and tender work for many client companies (e.g.
 * an accounting practice, a business consultancy) rather than only its
 * own business.
 * <p>
 * Deliberately depends on {@code compliancetender} rather than
 * duplicating its engine — same "one module owns the engine, a second
 * builds a different operating model on top of the same facade" pattern
 * already used for {@code ApFacade.createBillFromSupplyChainInvoice}
 * (Supply Chain owns matching/approval, AP owns the resulting bill).
 * <p>
 * Phase 1 — DONE: {@code ComplianceClient} (the client company itself)
 * and its CRUD, resolving the design question below. Deliberately just
 * the client entity for now, not client-specific compliance/tender
 * tracking — running compliancetender's engine per-client, rather than
 * per-tenant the way it works today, is a separate, larger data-model
 * decision for a later phase.
 * <p>
 * The open design question is RESOLVED: a dedicated {@code ComplianceClient}
 * entity that OPTIONALLY references an existing CRM customer via
 * {@code crmCustomerId} (a reference, not a copy — enriched with live
 * CRM data at read time, the same pattern {@code TenderPersonnel}
 * established for HR employee references in {@code compliancetender}),
 * rather than extending {@code crm.Customer} directly. See
 * {@code ComplianceClient}'s own Javadoc for the full reasoning.
 * <p>
 * allowedDependencies, Phase 1 — narrowed to what's actually used, not
 * what a later phase will need. Checked before finalizing: neither
 * {@code ComplianceClient} nor its service/controller import a single
 * type from {@code identity} or {@code compliancetender} — no numbering
 * was needed for a client record, and there's no per-client compliance
 * tracking yet for the engine to operate on. Declaring them now would
 * repeat exactly the mistake this session kept finding and correcting
 * elsewhere: a module boundary opened for a reason that doesn't match
 * what's actually built. Both get added back in the phase that actually
 * needs them — the same incremental discipline
 * {@code compliancetender}'s own package-info.java used for its first
 * boundary declaration:
 * <ul>
 *   <li>{@code shared} — baseline.</li>
 *   <li>{@code crm} — {@code CrmFacade.findCustomerById}/{@code customerExists}
 *       backing {@code ComplianceClient}'s optional reference to an
 *       existing CRM customer.</li>
 * </ul>
 * Later phase, when client-specific compliance/tender tracking is
 * actually built: {@code identity} (numbering, tenant details) and
 * {@code compliancetender} (the engine this module will operate a
 * second, multi-client model on top of).
 */
@ApplicationModule(allowedDependencies = {"shared", "crm"})
package za.co.handyflow.platform.complianceservices;

import org.springframework.modulith.ApplicationModule;
