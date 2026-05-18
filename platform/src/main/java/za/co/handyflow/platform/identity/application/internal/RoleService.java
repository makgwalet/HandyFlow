package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.identity.domain.model.Permission;
import za.co.handyflow.platform.identity.domain.model.Role;
import za.co.handyflow.platform.identity.domain.repository.PermissionRepository;
import za.co.handyflow.platform.identity.domain.repository.RoleRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public Role createDefaultAdminRole(TenantId tenantId) {
        Role admin = Role.create(
                tenantId,
                "ADMIN",
                "Full system administrator"
        );
        // WHY assign ALL permissions to ADMIN?
        // The first user of a tenant is the owner — they need full access.
        // They can create restricted roles for their employees later.
        List<Permission> allPermissions = permissionRepository.findAll();
        allPermissions.forEach(admin::addPermission);

        return roleRepository.save(admin);
    }
}
