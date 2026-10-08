package za.co.handyflow.platform.clinic.dto;

import java.util.List;

/** The permission catalogue and role templates, for the screen where a practice builds its roles. */
public final class AccessCatalogueDtos {
    private AccessCatalogueDtos() {}

    /** legacy is the coarse permission whose holders were given this one automatically; null means never automatic. */
    public record PermissionInfo(String code, String group, String kind, String legacy, boolean enforced, String description) {}

    public record RoleTemplateInfo(String key, String name, String description, List<String> permissions) {}

    public record Catalogue(List<PermissionInfo> permissions, List<String> legacyPermissions, List<RoleTemplateInfo> roleTemplates) {}
}
