/**
 * Secure Document Access Engine. Platform-owned; used by Projects, Contracting and Facilities Management.
 *
 * It controls ACCESS to documents and nothing else: per-recipient expiring links, email one-time codes,
 * short sessions, expiry, immediate revocation, per-document permission, download and access audit,
 * integrity (file hash frozen at issue), and email delivery. It owns no business workflow. The module that
 * owns the business record asks the engine to open a share, and implements {@code SecureShareProvider} so
 * the engine asks the owning module's own rules on EVERY view and download: a link can never bypass them.
 *
 * Depends only on shared. Modules depend on it, never the other way round.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "shared::*"})
package za.co.handyflow.platform.secureaccess;
