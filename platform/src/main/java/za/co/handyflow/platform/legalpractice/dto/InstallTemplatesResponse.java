package za.co.handyflow.platform.legalpractice.dto;

import java.util.List;

public record InstallTemplatesResponse(int added, int alreadyThere, List<String> addedCodes) {}
