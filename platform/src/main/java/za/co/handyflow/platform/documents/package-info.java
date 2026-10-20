/**
 * Document Service: metadata in PostgreSQL, file content in private storage behind the shared {@code FileStorageService} port (so the
 * storage provider can change without touching callers). Documents belong to an owner (a type and an id) inside one tenant, have
 * immutable versions, are archived and never deleted, are validated and passed to a malware scanner hook before they are stored, and
 * every change and download is audited. Callers (modules) decide who may see or change their own owners' documents; this module
 * guarantees tenant isolation and the rules above.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "audittrail"})
package za.co.handyflow.platform.documents;
