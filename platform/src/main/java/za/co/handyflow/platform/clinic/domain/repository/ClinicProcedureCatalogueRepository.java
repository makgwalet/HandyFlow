package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicProcedureCatalogue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicProcedureCatalogueRepository extends JpaRepository<ClinicProcedureCatalogue, UUID> {

    @Query("""
        SELECT p FROM ClinicProcedureCatalogue p
        WHERE p.active = true
        AND (LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
             OR p.tariffCode        LIKE CONCAT('%', :search, '%'))
        ORDER BY p.tariffCode
        """)
    List<ClinicProcedureCatalogue> search(String search);

    @Query("SELECT p FROM ClinicProcedureCatalogue p WHERE p.active = true ORDER BY p.tariffCode")
    List<ClinicProcedureCatalogue> findAllActive();

    @Query("SELECT p FROM ClinicProcedureCatalogue p WHERE p.tariffCode = :tariffCode AND p.active = true")
    Optional<ClinicProcedureCatalogue> findByTariffCode(String tariffCode);

    @Query("""
        SELECT p FROM ClinicProcedureCatalogue p
        WHERE p.active = true
        AND (:specialty IS NULL OR p.specialty = :specialty)
        ORDER BY p.tariffCode
        """)
    List<ClinicProcedureCatalogue> findBySpecialty(String specialty);
}
