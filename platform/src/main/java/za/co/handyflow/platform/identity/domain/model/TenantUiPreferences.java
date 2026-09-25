package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-wide appearance defaults and brand colour. See V302 for precedence
 * rules. A tenant without a row gets {@link #systemDefaults(UUID)}.
 */
@Entity
@Table(name = "tenant_ui_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantUiPreferences {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "brand_color", nullable = false, length = 20)
    private UiBrandColor brandColor = UiBrandColor.NAVY;

    @Column(name = "brand_locked", nullable = false)
    private boolean brandLocked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_theme", nullable = false, length = 10)
    private UiThemeMode defaultTheme = UiThemeMode.LIGHT;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_sidebar", nullable = false, length = 10)
    private UiSidebarMode defaultSidebar = UiSidebarMode.FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_container", nullable = false, length = 10)
    private UiContainerMode defaultContainer = UiContainerMode.BOXED;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Unsaved defaults that match today's look. Used when no row exists. */
    public static TenantUiPreferences systemDefaults(UUID tenantId) {
        TenantUiPreferences p = new TenantUiPreferences();
        p.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        return p;
    }

    public void update(UiBrandColor brandColor, boolean brandLocked, UiThemeMode defaultTheme,
                       UiSidebarMode defaultSidebar, UiContainerMode defaultContainer, UUID updatedBy) {
        this.brandColor = Objects.requireNonNull(brandColor, "brandColor");
        this.brandLocked = brandLocked;
        this.defaultTheme = Objects.requireNonNull(defaultTheme, "defaultTheme");
        this.defaultSidebar = Objects.requireNonNull(defaultSidebar, "defaultSidebar");
        this.defaultContainer = Objects.requireNonNull(defaultContainer, "defaultContainer");
        this.updatedBy = updatedBy;
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
