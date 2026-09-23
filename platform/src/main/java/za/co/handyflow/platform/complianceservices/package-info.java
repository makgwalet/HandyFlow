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
 * NOT started yet — see the strategic roadmap backlog, Part 6, for an
 * open design question this module's first real implementation needs
 * to settle first: whether a "client company" here is an extension of
 * CRM's {@code Customer} (reusing {@code CustomerType}) or a dedicated
 * {@code ComplianceClient} entity that references a CRM customer when
 * one exists without requiring one. This package-info.java exists to
 * declare the module boundary and reserve the name — not to imply
 * Phase 1 work has started here.
 * <p>
 * allowedDependencies, Phase 1 (once real work starts):
 * <ul>
 *   <li>{@code shared} — baseline.</li>
 *   <li>{@code identity} — same reasons as {@code compliancetender}
 *       (numbering, tenant details).</li>
 *   <li>{@code compliancetender} — the engine this module operates a
 *       second, multi-client model on top of.</li>
 *   <li>{@code crm} — pending the open design question above; needed
 *       either way for at least optionally referencing an existing CRM
 *       customer as a client company.</li>
 * </ul>
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "compliancetender", "crm"})
package za.co.handyflow.platform.complianceservices;

import org.springframework.modulith.ApplicationModule;
