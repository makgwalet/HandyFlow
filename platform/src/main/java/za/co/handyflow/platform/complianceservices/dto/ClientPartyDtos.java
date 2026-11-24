package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ClientPartyDtos {

    private ClientPartyDtos() {}

    public record SavePartyRequest(@NotBlank @Size(max = 255) String fullName, @Size(max = 10) String partyType,
                                   @Size(max = 10) String idType, @Size(max = 40) String idNumber, @NotBlank @Size(max = 20) String role,
                                   BigDecimal ownershipPercent, LocalDate appointedOn, LocalDate resignedOn, @Size(max = 2000) String notes) {}

    public record ResignRequest(LocalDate resignedOn) {}

    public record PartyResponse(UUID id, String fullName, String partyType, String idType, String idNumber, String role,
                                BigDecimal ownershipPercent, LocalDate appointedOn, LocalDate resignedOn, boolean active,
                                String notes, Instant createdAt) {}

    public record OwnershipView(BigDecimal shareholdingDeclared, boolean fullyDeclared, boolean hasDirector,
                                boolean hasBeneficialOwner, boolean beneficialOwnerGap, int directors) {}

    public record PartiesResponse(List<PartyResponse> parties, OwnershipView ownership) {}

    public record SaveSiteRequest(@NotBlank @Size(max = 200) String name, @Size(max = 20) String siteType,
                                  @Size(max = 1000) String address, Integer employeeCount) {}

    public record SiteResponse(UUID id, String name, String siteType, String address, Integer employeeCount, boolean active) {}
}
