package za.co.handyflow.platform.supplychain.domain.enums;

/** NONE = tracked and reported only; WARN = allowed but flagged; HARD_STOP = blocked unless an authorised override exists. */
public enum BudgetControlMode { NONE, WARN, HARD_STOP }
