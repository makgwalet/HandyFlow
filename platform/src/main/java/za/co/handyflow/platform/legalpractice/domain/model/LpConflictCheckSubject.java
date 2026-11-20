package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Set;
import java.util.UUID;

/** One name searched in a conflict check: the client themselves (role CLIENT) or another party to the matter. */
@Entity
@Table(name = "lp_conflict_check_subjects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpConflictCheckSubject {

    public static final Set<String> ROLES = Set.of("CLIENT", "OPPOSING_PARTY", "CO_PARTY", "RELATED_PARTY", "WITNESS", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "id_number", length = 100)
    private String idNumber;

    @Column(nullable = false, length = 20)
    private String role;

    public static LpConflictCheckSubject of(TenantId tenantId, UUID checkId, String name, String idNumber, String role) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Every party needs a name");
        if (role == null || !ROLES.contains(role)) throw new IllegalArgumentException("A party's role must be one of " + ROLES);
        LpConflictCheckSubject s = new LpConflictCheckSubject();
        s.tenantId = tenantId;
        s.checkId = checkId;
        s.name = name.trim();
        s.idNumber = idNumber == null || idNumber.isBlank() ? null : idNumber.trim();
        s.role = role;
        return s;
    }
}
