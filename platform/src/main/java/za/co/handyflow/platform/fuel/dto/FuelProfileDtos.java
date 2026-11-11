package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FuelProfileDtos {
    private FuelProfileDtos() {}

    public record DivisionView(FuelDivision division, String label, boolean active, Instant activatedAt, Instant deactivatedAt, String deactivationReason) {}
    public record ChecklistItem(String key, String label, boolean done) {}

    /** {@code legacyMode}: the tenant used Fuel before divisions existed and has every division until it completes setup. */
    public record ProfileView(String status, boolean legacyMode, String businessSize, String orgStructure, int wizardStep, String wizardAnswers,
                              List<FuelDivision> activeDivisions, List<DivisionView> divisions, List<ChecklistItem> checklist) {}

    public record WizardRequest(@Size(max = 16) String businessSize, @Size(max = 24) String orgStructure, int wizardStep, @Size(max = 8000) String wizardAnswers) {}
    public record ActivateRequest(@NotEmpty List<FuelDivision> divisions) {}
    public record DeactivateRequest(@NotBlank @Size(max = 300) String reason) {}

    public record LegalEntityRequest(@NotBlank @Size(max = 160) String name, @Size(max = 40) String registrationNo, @Size(max = 40) String taxNo,
                                     @Size(max = 40) String vatNo, @Size(max = 300) String address) {}
    public record LegalEntityResponse(UUID id, String name, String registrationNo, String taxNo, String vatNo, String address, String status, long depotCount) {}
    public record DepotEntityRequest(UUID legalEntityId) {}
}
