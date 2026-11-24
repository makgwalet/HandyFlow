package za.co.handyflow.platform.accounting.domain.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The VAT figures on a VAT201, worked out from the ledger (audit finding 1.3).
 *
 * <p>Output VAT is what the books owe SARS: credits to VAT Output (2100) less debits to it, so credit notes reduce it and
 * VAT posted by other modules (point of sale, warehousing, payroll bureau and so on) is included. Input VAT is what
 * can be claimed: debits to the VAT asset account(s) less credits. Whether an invoice is paid, overdue or unpaid does not
 * matter, because VAT is due when the invoice is issued.</p>
 *
 * <p>Pure: no database, no clock.</p>
 */
public final class VatReturnRules {

    public static final String OUTPUT_VAT_CODE = "2100";

    private VatReturnRules() {}

    public record Acct(UUID id, String code, String type, String subtype) {}

    public record Line(UUID accountId, BigDecimal debit, BigDecimal credit) {}

    public record Result(BigDecimal outputVat, BigDecimal inputVat, BigDecimal netVatPayable) {}

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    static boolean isOutput(Acct a) { return OUTPUT_VAT_CODE.equals(a.code()); }

    static boolean isInput(Acct a) { return "ASSET".equals(a.type()) && "VAT".equals(a.subtype()); }

    public static Result compute(List<Acct> accounts, List<Line> lines) {
        Map<UUID, Acct> byId = accounts.stream().collect(Collectors.toMap(Acct::id, Function.identity(), (a, b) -> a));
        BigDecimal output = BigDecimal.ZERO, input = BigDecimal.ZERO;
        for (Line l : lines) {
            Acct a = byId.get(l.accountId());
            if (a == null) continue;
            BigDecimal debit = nz(l.debit()), credit = nz(l.credit());
            if (isOutput(a)) output = output.add(credit.subtract(debit));
            if (isInput(a)) input = input.add(debit.subtract(credit));
        }
        return new Result(output, input, output.subtract(input));
    }
}
