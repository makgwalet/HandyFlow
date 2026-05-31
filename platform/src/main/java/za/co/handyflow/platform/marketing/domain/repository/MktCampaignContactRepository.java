package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.marketing.domain.model.MktCampaignContact;

import java.util.List;
import java.util.UUID;

public interface MktCampaignContactRepository extends JpaRepository<MktCampaignContact, UUID> {
    List<MktCampaignContact> findByCampaignId(UUID campaignId);
    long countByCampaignIdAndStatus(UUID campaignId, String status);
}
