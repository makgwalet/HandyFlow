/**
 * Facilities Management Company — Module 5b of the 11-module Track 7
 * build-out, the outsourced-provider sibling of Module 5a
 * ({@code facilities}). An FM company running site/asset maintenance for
 * multiple external client organizations: a client portfolio, the sites
 * and assets the FM company services on each client's behalf, planned
 * preventive maintenance schedules, work orders performed by the FM
 * company's own technician pool, per-client service agreements (a flat
 * monthly retainer or time-and-materials billing), real GL-posted
 * invoicing, and a client portal so each client can see their own sites'
 * work order status.
 * <p>
 * NO DEPENDENCY ON {@code facilities} IN EITHER DIRECTION — same
 * "internal department module" vs. "service provider module" separation
 * already established for every other pair this engagement (Debt
 * Collection, Warehousing/supplychain, Training/TrainingProvider). An FM
 * company's clients are external businesses, not the FM company's own
 * premises; conflating the two would mean an FM company's own head-office
 * maintenance requests would live in the same tables as the client work
 * they bill for, which is exactly the kind of data-shape confusion this
 * split exists to prevent.
 * <p>
 * ENTITY CONVENTION: standard plain-entity provider-module shape (see
 * {@code FmClient} etc. — {@code @Id UUID id = UUID.randomUUID()},
 * {@code @NoArgsConstructor(access = PROTECTED)}, boxed
 * {@code @Version Long version}) — the same convention every other
 * provider module in this engagement uses (RecruitmentAgency,
 * PayrollBureau, BookingAgency, Accountant, Auditor, CollectionsAgency,
 * Warehousing, TrainingProvider), and the same convention this module's
 * own internal sibling, {@code facilities}, independently arrived at by
 * matching {@code earthmoving}/{@code fleet} rather than {@code hr}.
 * <p>
 * REAL GL POSTING, UNLIKE THE INTERNAL VARIANT: {@code facilities}
 * (Module 5a) deliberately does not post maintenance cost to the ledger,
 * matching {@code fleet}/{@code earthmoving}'s own established behaviour
 * for their own internal maintenance cost tracking. This module is
 * different in kind — an FM company's work orders and retainers ARE its
 * revenue, not an internal cost centre — so {@code FmBillingService}
 * posts real journal entries via {@code AccountingFacade}, following the
 * exact AR-debit/Revenue-credit pattern established by
 * {@code ExpenseAccountingPoster}/{@code ClinicBillingService}/
 * {@code PosService}/{@code TrainProvBillingService}.
 * <p>
 * allowedDependencies: {@code shared}, {@code identity} (TenantFacade, for
 * branding portal invite emails with the FM company's own tenant details —
 * same reason every sibling provider module declares it), {@code billing}
 * (FeatureGuard), {@code accounting} (AccountingFacade, for real invoice
 * posting), {@code evidence} (photo attachments on work orders/assets),
 * {@code notifications} (daily compliance/overdue/invoice-overdue sweep).
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "billing", "accounting", "evidence", "notifications"})
package za.co.handyflow.platform.facilitiesmanagement;

import org.springframework.modulith.ApplicationModule;
