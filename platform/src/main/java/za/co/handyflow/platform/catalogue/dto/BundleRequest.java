package za.co.handyflow.platform.catalogue.dto;

import java.util.List;

/** The complete list of parts. It replaces what the package held before. */
public record BundleRequest(List<BundleLineRequest> components) {}
