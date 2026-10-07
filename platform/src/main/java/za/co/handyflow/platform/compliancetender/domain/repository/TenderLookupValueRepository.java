package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderLookupValue;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderLookupValueRepository extends JpaRepository<TenderLookupValue, UUID> {

    @Query("SELECT v FROM TenderLookupValue v WHERE v.tenantId.value = :#{#tenantId.value} ORDER BY v.listKey, LOWER(v.value)")
    List<TenderLookupValue> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT v FROM TenderLookupValue v WHERE v.tenantId.value = :#{#tenantId.value} AND v.listKey = :listKey AND LOWER(v.value) = LOWER(:value)")
    Optional<TenderLookupValue> findByValue(@Param("tenantId") TenantId tenantId, @Param("listKey") String listKey, @Param("value") String value);

    @Query("SELECT v FROM TenderLookupValue v WHERE v.tenantId.value = :#{#tenantId.value} AND v.id = :id")
    Optional<TenderLookupValue> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
