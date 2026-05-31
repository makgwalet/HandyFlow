package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.Permission;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(String name);

    /** Fetch a specific set of permissions by name — used when setting up default roles. */
    List<Permission> findByNameIn(Set<String> names);
}
