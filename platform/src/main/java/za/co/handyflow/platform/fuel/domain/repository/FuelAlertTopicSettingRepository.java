package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelAlertTopicSetting;

import java.util.List;
import java.util.UUID;

public interface FuelAlertTopicSettingRepository extends JpaRepository<FuelAlertTopicSetting, FuelAlertTopicSetting.Key> {

    @Query("SELECT s FROM FuelAlertTopicSetting s WHERE s.tenantId = :tenantId")
    List<FuelAlertTopicSetting> findAllForTenant(UUID tenantId);

    @Query("SELECT s FROM FuelAlertTopicSetting s WHERE s.tenantId = :tenantId AND s.topic = :topic")
    java.util.Optional<FuelAlertTopicSetting> findOne(UUID tenantId, String topic);
}
