/**
 * Tender Pricing: the SHARED, PURE price arithmetic behind a tender's price schedule (ADR-004).
 * <p>
 * Like {@code businessreadiness}, this module holds logic only: a stateless calculator over neutral records. It has no entities, no repositories and no dependencies, so
 * {@code compliancetender} (the company's own tenders) and {@code complianceservices} (a client's tenders) price the same way, with one tested set of rules, while each keeps
 * its own tables and services (the Part 8 parallel-entities decision recorded in complianceservices' package-info is not changed by this).
 */
@ApplicationModule(displayName = "Tender Pricing", allowedDependencies = {})
package za.co.handyflow.platform.tenderpricing;

import org.springframework.modulith.ApplicationModule;
