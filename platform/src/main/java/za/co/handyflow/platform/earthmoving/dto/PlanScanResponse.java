package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;

public record PlanScanResponse(int checked, int workOrdersRaised, List<String> raised) {}
