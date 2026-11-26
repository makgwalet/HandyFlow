package za.co.handyflow.platform.payrollbureau.dto;

/** Why a finalised pay run is being reversed. Required. */
public record ReversePayRunRequest(String reason) {}
