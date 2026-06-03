package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "AccountantJournalLine")
@Table(name = "prac_journal_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccJournalLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",  nullable = false) private UUID       tenantId;
    @Column(name = "journal_id", nullable = false) private UUID       journalId;
    @Column(name = "account_id", nullable = false) private UUID       accountId;
    @Column(name = "description")                  private String     description;

    @Column(name = "debit",  nullable = false, precision = 15, scale = 2)
    private BigDecimal debit  = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "vat_amount", precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "vat_type")
    private String vatType;     // OUTPUT, INPUT, EXEMPT, ZERO_RATED

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    // ── Factories ─────────────────────────────────────────────────────────────

    public static AccJournalLine debit(UUID tenantId, UUID journalId, UUID accountId,
                                       BigDecimal amount, String description, int order) {
        AccJournalLine l = new AccJournalLine();
        l.tenantId    = tenantId;
        l.journalId   = journalId;
        l.accountId   = accountId;
        l.debit       = amount;
        l.credit      = BigDecimal.ZERO;
        l.description = description;
        l.lineOrder   = order;
        return l;
    }

    public static AccJournalLine credit(UUID tenantId, UUID journalId, UUID accountId,
                                        BigDecimal amount, String description, int order) {
        AccJournalLine l = new AccJournalLine();
        l.tenantId    = tenantId;
        l.journalId   = journalId;
        l.accountId   = accountId;
        l.debit       = BigDecimal.ZERO;
        l.credit      = amount;
        l.description = description;
        l.lineOrder   = order;
        return l;
    }

    public static AccJournalLine debitWithVat(UUID tenantId, UUID journalId, UUID accountId,
                                              BigDecimal amount, BigDecimal vatAmount,
                                              String vatType, String description, int order) {
        AccJournalLine l = debit(tenantId, journalId, accountId, amount, description, order);
        l.vatAmount = vatAmount;
        l.vatType   = vatType;
        return l;
    }

    public static AccJournalLine creditWithVat(UUID tenantId, UUID journalId, UUID accountId,
                                               BigDecimal amount, BigDecimal vatAmount,
                                               String vatType, String description, int order) {
        AccJournalLine l = credit(tenantId, journalId, accountId, amount, description, order);
        l.vatAmount = vatAmount;
        l.vatType   = vatType;
        return l;
    }

    public boolean isDebit()  { return debit.compareTo(BigDecimal.ZERO)  > 0; }
    public boolean isCredit() { return credit.compareTo(BigDecimal.ZERO) > 0; }
}
