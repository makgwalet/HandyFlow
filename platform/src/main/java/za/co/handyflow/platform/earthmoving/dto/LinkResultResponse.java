package za.co.handyflow.platform.earthmoving.dto;

public record LinkResultResponse(int deploymentsLinkedToSite, int deploymentsLinkedToCustomer, int deploymentsLinkedToProject, int groupsStillUnresolved) {}
