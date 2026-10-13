package za.co.handyflow.platform.debtcollection.domain.model;

/**
 * How to pick among collectors who are eligible for a case.
 * ROUND_ROBIN: whoever was assigned a case longest ago (never-assigned first) — an even rotation.
 * LEAST_LOADED: fewest open cases (ties: longest since last assignment).
 * LEAST_VALUE: lowest total outstanding value held (ties: fewest cases) — balances money, not headcount.
 */
public enum AllocationStrategy { ROUND_ROBIN, LEAST_LOADED, LEAST_VALUE }
