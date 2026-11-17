package za.co.handyflow.platform.supplychain.domain.enums;

/** DRAFT -> OPEN -> CLOSED -> AWARDED ; DRAFT/OPEN/CLOSED -> CANCELLED ; OPEN <-> CLOSED via extend. */
public enum RfqStatus { DRAFT, OPEN, CLOSED, AWARDED, CANCELLED }