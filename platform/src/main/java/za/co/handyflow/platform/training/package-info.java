/**
 * Training / Learning &amp; Development — INTERNAL variant (Module 4a).
 * A tenant running training and skills-development for its own
 * employees: course catalogue, scheduled sessions, enrollments,
 * completions, and certifications.
 * <p>
 * WHY THIS DEPENDS DIRECTLY ON {@code hr}, UNLIKE debtcollection/
 * legalcompliance's OWN internal-module precedent: those two modules
 * are case/matter-management systems that reference a CRM customer by
 * id only (a snapshot, no facade dependency at all). This module is
 * different — its entire reason to exist is training THIS tenant's OWN
 * employees, so an employee reference isn't optional context, it's the
 * central relationship. `recruiter` and `contracting` already
 * established the real precedent for exactly this shape of dependency
 * (see their own package-info.java — both declare "hr" as an allowed
 * dependency and call `HrFacade` directly), so this module follows
 * that confirmed pattern rather than inventing a new one or reaching
 * past the module boundary the way `recruiter` originally did before
 * `HrFacade` existed (see `HrFacade`'s own Javadoc for that history).
 * <p>
 * WHY THE ENTITY CONVENTION HERE IS THE PLAIN-ENTITY STYLE, NOT
 * debtcollection/legalcompliance's AggregateRoot: this was an open
 * question flagged before any code was written (see the accompanying
 * status doc). `HrEmployee` itself — confirmed by direct source
 * read — is a plain {@code @Entity} (no shared superclass, id assigned
 * in a static {@code create()} factory, manual createdAt/updatedAt/
 * deletedAt, {@code @Version long version} primitive, String status).
 * Since this module's entities constantly reference `HrEmployee` by id
 * and mirror its lifecycle conventions (soft-delete on the catalogue,
 * status-string state machines), matching HR's own shape is more
 * consistent than reaching for the AggregateRoot convention of two
 * modules (`debtcollection`, `legalcompliance`) this module has no
 * other relationship to. AggregateRoot's domain-event-publishing
 * capability is also unused by either of those two modules today, so
 * nothing is given up by not extending it.
 * <p>
 * WHY THIS IS ITS OWN FEATURE-GATED, SEPARATELY-SUBSCRIBABLE MODULE
 * (billing/FeatureGuard, its own TRAINING_READ/MANAGE/ADMIN
 * permissions and module_catalogue row) RATHER THAN BUNDLED INSIDE
 * `hr` THE WAY `hr` ITSELF APPEARS TO BE UN-GATED: confirmed by direct
 * read of HrController — HR's own endpoints call no
 * `FeatureGuard.requireModule(...)`, suggesting HR is treated as a
 * bundled core capability, not an optional add-on. `debtcollection` is
 * also an "internal department" module in this engagement's own
 * classification and IS FeatureGuard-gated with its own permission
 * triplet — so "internal" does not imply "bundled/ungated" anywhere
 * else in this codebase. Per the platform's own SaaS principle that
 * every module must be independently subscribable (Tenant E: Projects
 * + Tasks only, no HR/Training), Training/L&amp;D follows the
 * debtcollection/legalcompliance/warehousing precedent: a real,
 * separately-priced module_catalogue entry, not silently bundled into
 * `hr`. FLAGGED for your review, not silently assumed either way — see
 * the status doc.
 * <p>
 * `evidence` is included for attaching attendance registers, training
 * materials, and external invoices to sessions. `notifications` for
 * the daily upcoming-session / expiring-certificate sweep.
 */
@ApplicationModule(allowedDependencies = {"shared", "hr", "billing", "notifications", "evidence"})
package za.co.handyflow.platform.training;

import org.springframework.modulith.ApplicationModule;
