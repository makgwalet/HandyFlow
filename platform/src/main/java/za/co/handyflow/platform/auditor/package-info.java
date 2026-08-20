/**
 * Auditor — Stage 3 of the Financial Control & Assurance adoption
 * plan. Gives an external auditor their own read-only portal login to
 * review a tenant's Evidence and Control Exceptions directly.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "evidence", "controls"})
package za.co.handyflow.platform.auditor;