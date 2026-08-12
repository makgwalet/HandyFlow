package za.co.handyflow.platform.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PersonIdentityRepository extends JpaRepository<PersonIdentity, UUID> {

    @Query("SELECT p FROM PersonIdentity p WHERE p.tenantId = :tenantId AND p.idNumber = :idNumber")
    Optional<PersonIdentity> findByTenantAndIdNumber(@Param("tenantId") UUID tenantId,
                                                     @Param("idNumber") String idNumber);

    @Query("SELECT p FROM PersonIdentity p WHERE p.tenantId = :tenantId AND LOWER(p.email) = LOWER(:email)")
    Optional<PersonIdentity> findByTenantAndEmail(@Param("tenantId") UUID tenantId,
                                                  @Param("email") String email);
}