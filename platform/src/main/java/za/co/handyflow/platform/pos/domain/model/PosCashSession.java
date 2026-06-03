package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a cashier's register session — opened at the start of a shift with
 * a known float, closed at end-of-day with a physical cash count.
 *
 * Rules enforced by PosService:
 *  - CASH transactions are blocked if no OPEN session exists for the user.
 *  - Voiding a transaction inside a closed session requires POS_ADMIN.
 *  - A session cannot be re-opened once CLOSED.
 */
@Entity
@Table(name = "pos_cash_sessions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosCashSession {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "session_number", nullable = false) private String     sessionNumber;
    @Column(name = "opened_by",      nullable = false) private UUID       openedBy;
    @Column(name = "opened_by_name", nullable = false) private String     openedByName;
    @Column(name = "closed_by")                        private UUID       closedBy;
    @Column(name = "closed_by_name")                   private String     closedByName;

    /** Cash placed in drawer at session open */
    @Column(name = "opening_float", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingFloat = BigDecimal.ZERO;

    /** Cash physically counted at session close */
    @Column(name = "closing_float", precision = 15, scale = 2)
    private BigDecimal closingFloat;

    /** Sum of all CASH transactions during this session (computed at close) */
    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;

    /** closingFloat - (openingFloat + expectedCash) — positive = over, negative = short */
    @Column(name = "cash_variance", precision = 15, scale = 2)
    private BigDecimal cashVariance;

    /** Total sales (all payment methods) during this session */
    @Column(name = "total_sales", precision = 15, scale = 2)
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount = 0;

    /** OPEN | CLOSED */
    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "notes")
    private String notes;

    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "closed_at")                   private Instant closedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PosCashSession open(TenantId tenantId, String sessionNumber,
                                      UUID openedBy, String openedByName,
                                      BigDecimal openingFloat, String notes) {
        PosCashSession s  = new PosCashSession();
        s.tenantId        = tenantId;
        s.sessionNumber   = sessionNumber;
        s.openedBy        = openedBy;
        s.openedByName    = openedByName;
        s.openingFloat    = openingFloat != null ? openingFloat : BigDecimal.ZERO;
        s.notes           = notes;
        s.status          = "OPEN";
        s.openedAt        = Instant.now();
        s.updatedAt       = Instant.now();
        return s;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Closes the session. Calculates variance = closingFloat - (openingFloat + expectedCash).
     *
     * @param closedBy        user closing the session
     * @param closedByName    display name
     * @param closingFloat    physical cash count in drawer
     * @param expectedCash    sum of all CASH sales during session
     * @param totalSales      sum of all sales (all payment methods)
     * @param transactionCount number of COMPLETED transactions
     */
    public void close(UUID closedBy, String closedByName,
                      BigDecimal closingFloat, BigDecimal expectedCash,
                      BigDecimal totalSales, int transactionCount,
                      String notes) {
        if ("CLOSED".equals(this.status)) {
            throw new IllegalStateException("Session " + sessionNumber + " is already closed");
        }
        this.closedBy         = closedBy;
        this.closedByName     = closedByName;
        this.closingFloat     = closingFloat;
        this.expectedCash     = expectedCash;
        this.totalSales       = totalSales;
        this.transactionCount = transactionCount;
        this.cashVariance     = closingFloat.subtract(openingFloat.add(expectedCash));
        this.notes            = notes != null ? notes : this.notes;
        this.status           = "CLOSED";
        this.closedAt         = Instant.now();
        this.updatedAt        = Instant.now();
    }

    public boolean isOpen()   { return "OPEN".equals(status); }
    public boolean isClosed() { return "CLOSED".equals(status); }
}
