package za.co.handyflow.platform.legalpractice.dto;

import java.util.List;

public final class AccessDtos {

    private AccessDtos() { }

    public record PermissionInfo(String code, String group, String kind, String level, boolean secondPerson, String description) { }

    public record TemplateInfo(String key, String name, String description, List<String> permissions, List<FindingInfo> findings) { }

    public record FindingInfo(String severity, String code, String message) { }

    public record Catalogue(List<PermissionInfo> permissions, List<TemplateInfo> templates) { }

    public record CheckRequest(List<String> permissions) { }

    public record CheckResult(int held, List<FindingInfo> findings) { }
}
