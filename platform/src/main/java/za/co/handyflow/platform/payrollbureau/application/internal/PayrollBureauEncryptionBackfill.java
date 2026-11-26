package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.EncryptedValue;
import za.co.handyflow.platform.shared.FieldEncryptionService;

import java.util.List;
import java.util.Map;

/**
 * One-off job: encrypts payroll bureau ID numbers, tax numbers and bank account numbers that were saved as plain
 * text before audit D3. Runs at startup only when {@code app.payrollbureau.encrypt-existing=true}, across all
 * tenants. Safe to run twice: rows already encrypted are skipped. Switch the property off again afterwards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payrollbureau.encrypt-existing", havingValue = "true")
public class PayrollBureauEncryptionBackfill implements ApplicationRunner {

    private static final String[][] TARGETS = {
            {"pay_employees", "id_number"},
            {"pay_employees", "tax_number"},
            {"pay_employees", "bank_account_number"},
            {"pay_employee_change_requests", "prev_bank_account"},
            {"pay_employee_change_requests", "new_bank_account"},
    };

    private final JdbcTemplate jdbc;
    private final FieldEncryptionService encryption;

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        for (String[] t : TARGETS) {
            total += encryptColumn(t[0], t[1]);
        }
        log.info("Payroll bureau encryption backfill finished: {} values encrypted", total);
    }

    int encryptColumn(String table, String column) {
        int done = 0;
        while (true) {
            // table and column names come from the constant list above, never from input
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, " + column + " AS v FROM " + table
                            + " WHERE " + column + " IS NOT NULL AND " + column + " <> '' AND " + column
                            + " NOT LIKE '" + EncryptedValue.PREFIX + "%' LIMIT 500");
            if (rows.isEmpty()) break;
            for (Map<String, Object> r : rows) {
                String plain = (String) r.get("v");
                jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?",
                        EncryptedValue.wrap(encryption.encrypt(plain)), r.get("id"));
                done++;
            }
        }
        log.info("Payroll bureau backfill: {}.{} -> {} encrypted", table, column, done);
        return done;
    }
}
