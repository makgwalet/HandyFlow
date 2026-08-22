/**
 * Approvals — backlog 1.1. Shared, generic approval/workflow engine any
 * module can depend on via ApprovalFacade, without approvals depending
 * back on any of them. Same shape as evidence (Stage 0) and controls
 * (Stage 1) of the Financial Control & Assurance adoption plan — this
 * is effectively that plan's next stage, following the identical
 * architectural template rather than inventing a new one.
 * <p>
 * Deliberately depends on nothing but shared — in particular, NOT on
 * identity. ROLE-type approval steps are resolved against the acting
 * user's own JWT authorities (passed in by the calling controller,
 * exactly how every other module's @PreAuthorize check already reads
 * authorities off the security context) rather than this module
 * querying identity for role membership. This keeps the engine
 * reusable by any module without pulling in a real cross-module
 * dependency for what the caller already has on hand.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package za.co.handyflow.platform.approvals;