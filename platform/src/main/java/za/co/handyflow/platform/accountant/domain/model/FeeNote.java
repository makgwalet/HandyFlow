package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "AccountantFeeNote")
@Table(name = "acc_fee_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeNote {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",      nullable = false) private UUID       tenantId;
    @Column(name = "client_id",      nullable = false) private UUID       clientId;
    @Column(name = "invoice_number", nullable = false) private String     invoiceNumber;
    @Column(name = "invoice_date",   nullable = false) private LocalDate  invoiceDate;
    @Column(name = "due_date",       nullable = false) private LocalDate  dueDate;

    @Column(name = "subtotal",  nullable = false, precision = 15, scale = 2) private BigDecimal subtotal;
    @Column(name = "vat_amount",nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount = BigDecimal.ZERO;
    @Column(name = "total",     nullable = false, precision = 15, scale = 2) private BigDecimal total;

    @Column(name = "status",       nullable = false) private String  status = "DRAFT";
    @Column(name = "recurring",    nullable = false) private boolean recurring;
    @Column(name = "recurrence_day")                 private Integer recurrenceDay;
    @Column(name = "fixed_fee", precision = 15, scale = 2) private BigDecimal fixedFee;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @Column(name = "sent_at")  private Instant sentAt;
    @Column(name = "paid_at")  private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false)                     private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "fee_note_id")
    @OrderBy("line_order ASC")
    private List<FeeNoteLine> lines = new ArrayList<>();

    // ── Factory ───────────────────────────────────────────────────────────────

    public static FeeNote create(UUID tenantId, UUID clientId, String invoiceNumber,
                                 LocalDate invoiceDate, LocalDate dueDate,
                                 BigDecimal subtotal, BigDecimal vatAmount) {
        FeeNote f = new FeeNote();
        f.tenantId      = tenantId;
        f.clientId      = clientId;
        f.invoiceNumber = invoiceNumber;
        f.invoiceDate   = invoiceDate;
        f.dueDate       = dueDate;
        f.subtotal      = subtotal;
        f.vatAmount     = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        f.total         = subtotal.add(f.vatAmount);
        f.createdAt     = Instant.now();
        f.updatedAt     = Instant.now();
        return f;
    }

    // ── State machine ─────────────────────────────────────────────────────────

    public void markSent() {
        this.status    = "SENT";
        this.sentAt    = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markPaid() {
        this.status    = "PAID";
        this.paidAt    = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markOverdue() {
        if (!"PAID".equals(status) && !"WRITTEN_OFF".equals(status)) {
            this.status    = "OVERDUE";
            this.updatedAt = Instant.now();
        }
    }

    public void writeOff() {
        this.status    = "WRITTEN_OFF";
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
