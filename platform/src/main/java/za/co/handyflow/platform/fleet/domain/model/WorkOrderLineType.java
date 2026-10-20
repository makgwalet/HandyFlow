package za.co.handyflow.platform.fleet.domain.model;

/** LABOUR quantities are hours; SUBLET is work done by an outside party on this order. */
public enum WorkOrderLineType {
    LABOUR, PART, SUBLET, OTHER
}