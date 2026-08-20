/**
 * Controls — Stage 1 of the Financial Control & Assurance adoption
 * plan. Shared, generic "needs attention" board any module can raise
 * exceptions onto via ControlExceptionFacade, without controls
 * depending back on any of them. Detect-only, per the plan's own
 * scope line — never enforces or blocks anything, only records what
 * another module already decided to flag.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package za.co.handyflow.platform.controls;