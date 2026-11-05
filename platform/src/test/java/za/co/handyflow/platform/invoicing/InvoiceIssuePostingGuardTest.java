package za.co.handyflow.platform.invoicing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-01: quote conversion, recurring invoices and the scheduler used to issue invoices without telling the ledger.
 * Any class that issues an invoice must also announce {@code InvoiceIssuedEvent}. This reads the source so a new way
 * of issuing cannot quietly skip the ledger (and, with it, the usage count that depends on the same invoices).
 */
class InvoiceIssuePostingGuardTest {

    @Test
    void everyClassThatIssuesAnInvoiceAnnouncesIt() throws IOException {
        Path root = Path.of("src/main/java/za/co/handyflow/platform/invoicing/application");
        List<String> missing = new ArrayList<>();
        int issuers = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                if (!src.contains("invoice.markIssued(")) continue;
                issuers++;
                if (!src.contains("InvoiceIssuedEvent")) missing.add(p.getFileName().toString());
            }
        }
        assertThat(issuers).as("the source folder was found and issuing classes were seen").isGreaterThanOrEqualTo(4);
        assertThat(missing).as("classes that issue invoices without InvoiceIssuedEvent").isEmpty();
    }
}
