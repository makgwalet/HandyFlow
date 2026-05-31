package za.co.handyflow.platform.billing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.billing.domain.model.ModuleCatalogue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleCatalogueRepository extends JpaRepository<ModuleCatalogue, UUID> {

    @Query("SELECT m FROM ModuleCatalogue m WHERE m.active = true ORDER BY m.sortOrder")
    List<ModuleCatalogue> findAllActive();

    Optional<ModuleCatalogue> findByKey(String key);
}