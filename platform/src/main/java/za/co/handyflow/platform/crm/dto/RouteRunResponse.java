package za.co.handyflow.platform.crm.dto;

/** routed = leads given an owner; left = open leads that still have no owner (no rule matched or nobody could take them). */
public record RouteRunResponse(int routed, int left) {}
