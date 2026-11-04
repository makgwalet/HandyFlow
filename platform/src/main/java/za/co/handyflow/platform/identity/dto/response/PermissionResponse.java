package za.co.handyflow.platform.identity.dto.response;

import java.util.UUID;

public record PermissionResponse(
        UUID   id,
        String name,
        String description,
        String group          // permission-name prefix (USER, HR, FLEET...); real module keys arrive with the Phase 1 catalogue
) {
    public static String groupOf(String name) {
        if (name == null) return "OTHER";
        int i = name.indexOf('_');
        return i > 0 ? name.substring(0, i) : name;
    }
}
