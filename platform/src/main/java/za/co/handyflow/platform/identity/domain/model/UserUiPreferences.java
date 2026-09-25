package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One user's appearance overrides. A null field means "inherit the tenant
 * default". Always loaded by (userId, tenantId) so a token for one tenant can
 * never read or write a row belonging to another.
 */
@Entity
@Table(name = "user_ui_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserUiPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 10)
    private UiThemeMode theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "sidebar", length = 10)
    private UiSidebarMode sidebar;

    @Enumerated(EnumType.STRING)
    @Column(name = "container", length = 10)
    private UiContainerMode container;

    @Enumerated(EnumType.STRING)
    @Column(name = "brand_color", length = 20)
    private UiBrandColor brandColor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pinned_modules", columnDefinition = "jsonb", nullable = false)
    private List<String> pinnedModules = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static UserUiPreferences create(UUID userId, UUID tenantId) {
        UserUiPreferences p = new UserUiPreferences();
        p.userId = Objects.requireNonNull(userId, "userId");
        p.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        return p;
    }

    /** Full replacement of the user's overrides; nulls mean "inherit". */
    public void update(UiThemeMode theme, UiSidebarMode sidebar, UiContainerMode container,
                       UiBrandColor brandColor, List<String> pinnedModules) {
        this.theme = theme;
        this.sidebar = sidebar;
        this.container = container;
        this.brandColor = brandColor;
        this.pinnedModules = pinnedModules == null ? new ArrayList<>() : new ArrayList<>(pinnedModules);
    }

    public List<String> getPinnedModules() {
        return pinnedModules == null ? List.of() : List.copyOf(pinnedModules);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
