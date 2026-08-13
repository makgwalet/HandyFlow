
/**
 * Booking Agency — outsourced-provider module for an agency managing
 * bookings/scheduling on behalf of external client businesses (e.g. a
 * virtual receptionist / call-answering service handling appointment
 * scheduling for several small businesses who don't run their own
 * booking system). Mirrors the HR/Payroll Bureau and Recruitment
 * Agency pattern — see either module's own package-info for the full
 * reasoning this replicates.
 * <p>
 * WHY A SEPARATE MODULE, NOT A MODE ON `bookings`: same reasoning as
 * every other outsourced-provider module in this platform — an agency's
 * clients aren't HandyFlow tenants themselves, and the agency needs its
 * own client-portfolio/billing layer with no equivalent in single-
 * tenant `bookings`. `bookings` stays untouched; this module mirrors
 * its proven domain shape (staff/service -> slot -> booking) without
 * importing its internal classes, same isolation discipline already
 * applied to hr/payrollbureau and recruiter/recruitmentagency.
 * <p>
 * REAL OPEN QUESTION, NOT DEFAULTED SILENTLY: the billing model for
 * this module — flat monthly retainer per client (recurring,
 * subscription-shaped, like Payroll Bureau) vs. per-booking transaction
 * fee (volume-based, like a call-center pricing model) — genuinely
 * depends on which real business this is modeling, and wasn't
 * specified. The foundation layer below doesn't depend on that answer;
 * the billing layer, when built, will need it resolved rather than
 * guessed.
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "billing"})
package za.co.handyflow.platform.bookingagency;

import org.springframework.modulith.ApplicationModule;
