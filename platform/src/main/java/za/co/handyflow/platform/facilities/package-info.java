/**
 * Facilities &amp; Maintenance (Internal) — Module 5a of the 11-module Track 7
 * build-out. An SME's own in-house facilities function: a register of the
 * sites/premises it occupies, the physical building assets in them (HVAC
 * plant, generators, fire equipment, elevators, electrical/plumbing
 * infrastructure), planned preventive maintenance (PPM) schedules for those
 * assets, the work orders (job cards) that PPM schedules and reactive
 * breakdowns/requests both generate, an internal technician pool, an
 * external vendor/contractor pool for work the internal team can't do
 * itself, and compliance-certificate tracking (electrical COC, fire
 * equipment service certificates, elevator/lift certificates, gas
 * compliance) — a genuine South African SME need with real regulatory
 * consequences if it lapses.
 * <p>
 * SCOPE CONFIRMED AGAINST THE REAL REPO BEFORE ANY CODE WAS WRITTEN: a
 * project-wide search found no existing Facilities/Maintenance module.
 * {@code property} tracks landlord/lessee leases and can flag a {@code Unit}
 * as status {@code MAINTENANCE}, but implements no actual maintenance
 * tracking behind that flag — no asset register, no work orders, no
 * technicians, no PPM. {@code earthmoving} and {@code fleet} each maintain
 * their own asset type (earthmoving plant, vehicles) with their own
 * maintenance-record sub-entity, but neither is a general building/premises
 * facilities system, and this module deliberately does not touch either —
 * a generator or an HVAC unit is not earthmoving plant or a vehicle.
 * {@code desk} is generic support ticketing (helpdesk/internal channels)
 * with no asset, technician, or PPM concept at all. This is a clean new
 * module, not a duplicate of anything already built.
 * <p>
 * ENTITY CONVENTION: plain-entity provider-module shape (see
 * {@code FacilityAsset} etc. — {@code @Id UUID id = UUID.randomUUID()},
 * {@code @NoArgsConstructor(access = PROTECTED)}, boxed
 * {@code @Version Long version}, manual {@code createdAt}/{@code updatedAt},
 * String status fields) — the same convention {@code earthmoving} and
 * {@code fleet} themselves already use for their own asset/maintenance
 * entities (confirmed by direct read of {@code EarthAsset}/
 * {@code MaintenanceRecord}/{@code Vehicle} before writing a line of this
 * module). This module has no relationship to {@code hr}'s own entity shape
 * and no reason to mirror it — unlike {@code training} (Module 4a), which
 * mirrors {@code HrEmployee} specifically because it constantly references
 * employees by id via {@code HrFacade}. Facilities technicians are tracked
 * as their own lightweight {@code FacilityTechnician} record (name/contact/
 * specialization only) rather than linked to {@code HrEmployee} — matching
 * how {@code fleet}'s own {@code Vehicle.assignedDriverId} is a bare,
 * unvalidated UUID reference with no FK integrity and no {@code hr}
 * dependency declared in {@code fleet}'s own {@code package-info.java}
 * (confirmed by direct read). Generalizing to a real {@code HrFacade}
 * lookup would be a genuine, separate enhancement — flagged in this
 * module's status report, not silently assumed.
 * <p>
 * NO GL POSTING FOR INTERNAL MAINTENANCE COST: work-order cost (labour +
 * materials + any vendor invoice) is tracked as a plain number on the work
 * order for reporting, exactly like {@code fleet}'s own
 * {@code VehicleCostSummaryResponse} and {@code earthmoving}'s maintenance
 * cost fields — neither of those posts to the real ledger either. A
 * facilities cost feeding {@code AccountingFacade} as a real expense journal
 * would be a legitimate future integration with {@code ap}/{@code accounting}
 * (via a bill/expense claim) — deliberately out of scope here to match the
 * two closest sibling modules' own established behaviour, not an oversight.
 * <p>
 * allowedDependencies: {@code shared} (TenantId/TenantContext plumbing),
 * {@code billing} (FeatureGuard — this module is a separately-subscribable
 * add-on, matching every other module's own gating), {@code evidence}
 * (photo attachments on assets, work orders, and compliance certificates —
 * a fire-equipment service photo or a completed-repair photo is exactly
 * the kind of attachment {@code EvidenceFacade} already exists for),
 * {@code notifications} (PPM-due, work-order, and compliance-expiry
 * alerts, matching every other module's own daily-sweep scheduler
 * pattern).
 */
@ApplicationModule(allowedDependencies = {"shared", "billing", "evidence", "notifications"})
package za.co.handyflow.platform.facilities;

import org.springframework.modulith.ApplicationModule;
