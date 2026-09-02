package za.co.handyflow.platform.agriculture.dto;

import java.util.UUID;

/** Null productionAreaId is valid — clears the animal's current area assignment. */
public record MoveAnimalRequest(UUID productionAreaId) {}
