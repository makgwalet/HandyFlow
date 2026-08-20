/**
 * Evidence — Stage 0 of the Financial Control & Assurance adoption
 * plan. Shared, generic evidence-attachment capability any module can
 * depend on via EvidenceFacade, without evidence depending back on any
 * of them.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package za.co.handyflow.platform.evidence;