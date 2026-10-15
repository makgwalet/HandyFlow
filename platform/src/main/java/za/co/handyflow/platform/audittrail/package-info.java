/**
 * Audit trail: an append-only record of who did what to which audit item, with before and after values. Written by the
 * audit-related modules through {@link za.co.handyflow.platform.audittrail.application.AuditTrailFacade}; nothing here
 * can be edited or deleted (enforced by a database trigger in V409).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package za.co.handyflow.platform.audittrail;
