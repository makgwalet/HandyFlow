/**
 * Business Readiness: a SHARED, PURE evaluation of whether a business meets a set of requirements (ADR-003).
 * <p>
 * This module holds logic only: neutral input records and a stateless evaluator. It has no entities, no repositories, no database and no dependencies,
 * so it can be called by {@code compliancetender} (the tenant's own business) and by {@code complianceservices} (a client's business) alike.
 * <p>
 * That is deliberate. {@code complianceservices} keeps its own parallel entities (the Part 8 architecture decision recorded in its package-info) and never
 * references {@code compliancetender}'s Java code; this module does not change that. The two modules still keep their own tables and services, and each maps its own
 * entities to the neutral facts here. What is shared is the RULES, so a requirement is judged the same way for a tenant and for a client and the logic is written, tested
 * and fixed once instead of twice.
 */
@ApplicationModule(displayName = "Business Readiness", allowedDependencies = {})
package za.co.handyflow.platform.businessreadiness;

import org.springframework.modulith.ApplicationModule;
