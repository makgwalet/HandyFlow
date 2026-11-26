package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A catalogue entry: something that can be added to or taken from pay. Its role fixes how it is taxed. */
@Entity
@Table(name = "pay_components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayComponent {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "sars_code")
    private String sarsCode;

    @Column(name = "standard", nullable = false)
    private boolean standard;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | ARCHIVED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public static PayComponent create(UUID tenantId, String code, String name, String role, String sarsCode, boolean standard) {
        PayComponent c = new PayComponent();
        c.tenantId = tenantId;
        c.code = code.trim().toUpperCase();
        c.name = name.trim();
        c.role = role;
        c.sarsCode = sarsCode == null || sarsCode.isBlank() ? null : sarsCode.trim();
        c.standard = standard;
        return c;
    }

    public void archive() { this.status = "ARCHIVED"; }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
