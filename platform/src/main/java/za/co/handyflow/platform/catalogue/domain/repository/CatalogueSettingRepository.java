package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueSetting;

import java.util.UUID;

public interface CatalogueSettingRepository extends JpaRepository<CatalogueSetting, UUID> {}
