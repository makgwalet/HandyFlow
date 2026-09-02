/**
 * Agriculture — a new flagship operational vertical for HandyFlow,
 * inserted ahead of the remaining Track 7 provider-module sequence. Build
 * Increment 1 (this delivery): Farm Foundation + Livestock. Build
 * Increment 2 (next): Crops. Poultry (including hatchery), Aquaculture,
 * Farm Map/GIS, weather, satellite/IoT integrations, AI anomaly
 * detection, and the marketplace/finance-readiness ecosystem are all
 * deliberately out of scope for this module as it stands — sequenced,
 * not rejected. See the full architecture decision document
 * ({@code claude/HandyFlow-Agriculture-Architecture-Plan.md} in the
 * project) for the reasoning behind every decision summarized here.
 * <p>
 * NOT A PROVIDER MODULE: unlike CollectionsAgency/TrainingProvider/
 * FacilitiesManagement/Bookkeeping (a firm serving external clients),
 * Agriculture is an OPERATIONAL module — one tenant running its own
 * farm(s), animals, and fields. Structurally it is closest to
 * {@code earthmoving}/{@code fleet} (a tenant tracking its own physical
 * productive assets), not to any of the split internal/provider module
 * pairs. There is accordingly no internal-vs-provider variant question
 * for this module — a "multi-farm operation" is just one tenant with
 * multiple {@code AgFarm} records, the same way one tenant can have
 * multiple {@code fleet.Vehicle}s.
 * <p>
 * NO GL POSTING, NO BESPOKE BILLING ENGINE: confirmed by direct source
 * read that neither {@code earthmoving} nor {@code fleet} lists
 * {@code accounting} as an allowed dependency, and neither posts journal
 * entries — operational cost is a reporting number, not a ledger event.
 * Agriculture follows the same precedent. Separately, and more
 * significantly: Agriculture does NOT build its own invoice/sale entity
 * the way the provider modules do. A farm selling its own cattle or eggs
 * is the same kind of commercial event as any other tenant selling any
 * other product — that belongs in the platform's existing
 * {@code invoicing}/{@code crm} modules, not duplicated here. This
 * module's job is to record what was produced and what it cost to
 * produce (see {@code AgHarvest}-equivalent concepts arriving with the
 * Crops increment); the sale itself is out of this module's scope by
 * design, not by omission.
 * <p>
 * HR LINKAGE — A DELIBERATE DEPARTURE FROM {@code earthmoving}/
 * {@code fleet}'S OWN PRECEDENT, confirmed with you explicitly: those two
 * modules track operators/drivers as free text or unvalidated UUIDs
 * (they predate {@code HrFacade} or chose not to use it). {@code
 * training} (built this engagement) already established the better
 * pattern — a real, validated employee reference via
 * {@code HrFacade.findEmployeeById()}/{@code employeeExists()} plus a
 * name snapshot for display. Agriculture follows {@code training}'s
 * precedent rather than {@code earthmoving}/{@code fleet}'s, because
 * labour-cost-per-hectare/per-animal reporting is only meaningful against
 * a real employee record, not a free-text name.
 * <p>
 * NO {@code supplychain} REUSE: {@code supplychain} exposes no public
 * facade ({@code ScmService} is internal-only) — same situation
 * {@code facilitiesmanagement} and {@code warehousing} already hit and
 * resolved the same way. Agriculture owns its own feed/seed/fertiliser/
 * chemical inventory ({@code AgInventoryItem}/{@code AgStockMovement}),
 * scoped per farm.
 * <p>
 * NO {@code tasks} REUSE: {@code tasks} exposes no public facade either
 * (no {@code TasksFacade} exists anywhere in the codebase) — "vaccination
 * due -> auto-create a task" is not achievable by pushing into the
 * generic Tasks module today. {@code facilities} (Module 5a) hit the
 * identical situation with its own PPM-schedule-due workflow and solved
 * it the same way this module does: generate the module's own actionable
 * record (here, an {@code AgHealthEvent} with a future
 * {@code nextDueDate}) and surface it via the daily notification sweep,
 * rather than depending on a cross-module Tasks integration that doesn't
 * exist yet. A general {@code TasksFacade} would be a legitimate
 * platform-level enhancement affecting every module, not something this
 * module's own scope should solve unilaterally.
 * <p>
 * INDIVIDUAL VS. GROUP TRACKING — the central design decision of the
 * livestock domain: every history entity ({@code AgWeightRecord},
 * {@code AgHealthEvent}, {@code AgBreedingRecord}, {@code
 * AgMovementRecord}, {@code AgMortalityRecord}, {@code AgFeedRecord})
 * carries a nullable {@code animalId} AND a nullable {@code groupId},
 * with exactly one required — a cattle breeder tracking individual
 * animals and a broiler farm tracking a 15,000-bird batch as one
 * {@code AgGroup} both use the same entities, not a fork of the domain
 * model per tracking granularity.
 * <p>
 * ENTITY CONVENTION: the plain-entity, append-only-history convention
 * {@code earthmoving} itself uses for {@code MaintenanceRecord}/
 * {@code OperatorLog} ({@code @Entity}, {@code @Embedded TenantId},
 * boxed {@code @Version Long version}, static {@code create()} factories)
 * — the correct structural sibling to follow, since this module shares
 * {@code earthmoving}'s own "tenant operates its own physical assets"
 * shape far more than it shares any provider module's shape.
 * <p>
 * allowedDependencies: {@code shared}, {@code billing} (FeatureGuard),
 * {@code hr} (HrFacade — see above), {@code evidence} (scouting/
 * treatment/harvest photo attachments), {@code notifications} (daily
 * sweep for vaccinations/health follow-ups due).
 */
@ApplicationModule(allowedDependencies = {"shared", "billing", "hr", "evidence", "notifications"})
package za.co.handyflow.platform.agriculture;

import org.springframework.modulith.ApplicationModule;
