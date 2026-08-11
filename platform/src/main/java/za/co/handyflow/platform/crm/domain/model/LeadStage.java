package za.co.handyflow.platform.crm.domain.model;

/**
 * LeadStage — sales pipeline progression for LEAD-type customers.
 *
 * FIX: "no lead/pipeline stage tracking" gap — CustomerType already
 * distinguished LEAD from CUSTOMER, but nothing tracked WHERE in the
 * funnel a lead actually was. The audit's own framing: this module "reads
 * more like a customer database than a sales pipeline tool" without it.
 * <p>
 * Applies to LEAD-type customers. A CUSTOMER (already converted) has no
 * meaningful "pipeline stage" — Customer.changeStage() enforces this
 * rather than leaving it as an unenforced convention (see that method).
 */
public enum LeadStage {
    NEW,
    CONTACTED,
    QUALIFIED,
    WON,
    LOST
}