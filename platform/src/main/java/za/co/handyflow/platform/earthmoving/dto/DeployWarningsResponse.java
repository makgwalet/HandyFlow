package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;

/** Things to know before sending a machine out. Warnings only: they do not block the deployment. */
public record DeployWarningsResponse(List<String> warnings) {}
