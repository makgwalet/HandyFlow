package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;

public record TelematicsIngestResult(int accepted, int duplicates, List<String> rejected) {}
