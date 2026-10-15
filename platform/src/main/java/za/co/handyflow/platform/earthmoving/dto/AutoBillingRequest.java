package za.co.handyflow.platform.earthmoving.dto;

/** Put a deployment on the monthly run with this basis, or take it off with a null basis. */
public record AutoBillingRequest(String basis) {}
