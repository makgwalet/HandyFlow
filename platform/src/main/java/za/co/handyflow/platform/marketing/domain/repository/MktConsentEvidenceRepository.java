package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.marketing.domain.model.MktConsentEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface MktConsentEvidenceRepository extends JpaRepository<MktConsentEvidence, UUID> {

    List<MktConsentEvidence> findByTenantIdAndEmailOrderByCreatedAtDesc(TenantId tenantId, String email);
}