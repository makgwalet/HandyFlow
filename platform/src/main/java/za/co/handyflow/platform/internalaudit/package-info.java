
// Internal Audit — Phase 1 of the design agreed with the product owner:
// Audit Universe, hybrid risk scoring (system-calculated + auditor
// override), Annual Audit Plan, and Engagement shell with
// engagement-scoped role assignments (distinct from system
// permissions — see EngagementAssignment's own class comment).
// Only "shared" and "identity" allowed for Phase 1 (identity needed
// purely to resolve user names for EngagementAssignmentResponse — see
// InternalAuditService's own mapper). Later phases (workpapers reusing
// the AccWorkpaper* pattern, GL sampling against AccJournalEntry,
// report sign-off via ApprovalFacade) will each need their own
// dependency added deliberately, not declared upfront for
// not-yet-written code.
@ApplicationModule(allowedDependencies = {"shared", "identity", "accounting"})
package za.co.handyflow.platform.internalaudit;

import org.springframework.modulith.ApplicationModule;
