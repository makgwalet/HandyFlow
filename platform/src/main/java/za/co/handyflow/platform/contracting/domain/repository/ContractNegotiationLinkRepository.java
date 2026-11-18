package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationLink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractNegotiationLinkRepository extends JpaRepository<ContractNegotiationLink, UUID> {
    Optional<ContractNegotiationLink> findByTokenHash(String tokenHash);

    Optional<ContractNegotiationLink> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ContractNegotiationLink> findByTenantIdAndContractIdOrderByCreatedAtDesc(UUID tenantId, UUID contractId);

    List<ContractNegotiationLink> findByTenantIdAndContractIdAndPartyIdAndRevokedAtIsNull(UUID tenantId, UUID contractId, UUID partyId);

    /** Revokes every live link of a contract, for example when negotiation ends or the contract is sent. Returns how many were revoked. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE ContractNegotiationLink l SET l.revokedAt = :now, l.revokedReason = :reason WHERE l.tenantId = :tenantId AND l.contractId = :contractId AND l.revokedAt IS NULL")
    int revokeAllActive(UUID tenantId, UUID contractId, String reason, Instant now);
}
