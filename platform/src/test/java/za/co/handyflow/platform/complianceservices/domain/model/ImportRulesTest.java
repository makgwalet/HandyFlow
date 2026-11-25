package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportRulesTest {

    static final TenantId T = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void parsesQuotesBomCrlfAndSemicolons() {
        List<List<String>> r = ImportRules.parseCsv("﻿Name,Notes\r\n\"Acme, Ltd\",\"said \"\"hi\"\"\"\r\n\r\nBeta,\r\n");
        assertThat(r.size() == 3).isTrue();
        assertThat(r.get(1).get(0).equals("Acme, Ltd")).isTrue();
        assertThat(r.get(1).get(1).equals("said \"hi\"")).isTrue();
        assertThat(ImportRules.parseCsv("a;b\n1;2").get(1).get(1).equals("2")).isTrue();
        assertThatThrownBy(() -> ImportRules.parseCsv("a,\"b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headerAliasesAndMissingColumns() {
        int[] pos = ImportRules.mapHeader("CLIENTS", List.of("Company Name", "Email Address", "Mobile"));
        assertThat(pos[0] == 0 && pos[1] == 1 && pos[2] == 2 && pos[3] == -1).isTrue();
        assertThatThrownBy(() -> ImportRules.mapHeader("CLIENTS", List.of("Email"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportRules.mapHeader("REGISTRATIONS", List.of("Client", "Authority"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportRules.mapHeader("CLIENTS", List.of("Name", "Company"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formulaPrefixIsNeutralised() {
        List<String> f = ImportRules.arrange(new int[]{0, 1}, List.of("=HYPERLINK(\"x\")", " ok "));
        assertThat(f.get(0).startsWith("'=")).isTrue();
        assertThat(f.get(1).equals("ok")).isTrue();
    }

    @Test
    void clientRowChecks() {
        assertThat(ImportRules.clientProblem(List.of("", "", "", "")) != null).isTrue();
        assertThat(ImportRules.clientProblem(List.of("Acme", "not-an-email", "", "")) != null).isTrue();
        assertThat(ImportRules.clientProblem(List.of("Acme", "a@b.co", "082", "")) == null).isTrue();
    }

    @Test
    void registrationRowChecks() {
        assertThat(ImportRules.registrationProblem(List.of("Acme", "CIPC", "Annual return", "K1", "2026-01-01", "2025-01-01", "")) != null).isTrue();
        assertThat(ImportRules.registrationProblem(List.of("Acme", "CIPC", "Annual return", "K1", "31/01/2026", "2027-01-31", "")) == null).isTrue();
        assertThat(ImportRules.registrationProblem(List.of("Acme", "CIPC", "", "", "", "", "")) != null).isTrue();
        assertThat(ImportRules.registrationProblem(List.of("Acme", "CIPC", "Type", "", "13/13/2026", "", "")) != null).isTrue();
        assertThat(ImportRules.isoDates(List.of("A", "B", "C", "D", "31/01/2026", "", "")).get(4).equals("2026-01-31")).isTrue();
        assertThat(ImportRules.date("") == null).isTrue();
    }

    @Test
    void keysIgnoreCaseAndSpacing() {
        assertThat(ImportRules.nameKey("  Acme   Holdings ").equals("acme holdings")).isTrue();
        assertThat(ImportRules.registrationKey("a", List.of("", "cipc", "Annual Return", "K1")).equals(ImportRules.registrationKey("a", List.of("", "CIPC", "annual return", "k1")))).isTrue();
    }

    @Test
    void storedFieldsRoundTripWithEmptyEnds() {
        List<String> f = List.of("Acme", "", "", "");
        assertThat(ImportRules.split(ImportRules.join(f)).equals(f)).isTrue();
    }

    @Test
    void commitMovesCountsAndHappensOnce() {
        ComplianceImportBatch b = ComplianceImportBatch.preview(T, "CLIENTS", "c.csv", 5, 1, 2, ME);
        assertThat(b.getTotalRows() == 8).isTrue();
        b.commit(ME, 3, 1, 1);
        assertThat(b.getOkRows() == 3 && b.getDuplicateRows() == 2 && b.getRejectedRows() == 3 && b.getImportedRows() == 3).isTrue();
        assertThat(b.getOkRows() + b.getDuplicateRows() + b.getRejectedRows() == b.getTotalRows()).isTrue();
        assertThatThrownBy(() -> b.commit(ME, 0, 0, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> b.discard(ME, "not allowed after commit")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void discardNeedsAReason() {
        ComplianceImportBatch b = ComplianceImportBatch.preview(T, "CLIENTS", "c.csv", 1, 0, 0, ME);
        assertThatThrownBy(() -> b.discard(ME, "no")).isInstanceOf(IllegalArgumentException.class);
        b.discard(ME, "Wrong file uploaded");
        assertThat(b.getStatus().equals("DISCARDED")).isTrue();
    }

    @Test
    void onlyOkRowsCanBeImportedOrSetAside() {
        ComplianceImportRow ok = ComplianceImportRow.of(T, UUID.randomUUID(), 2, List.of("Acme", "", "", ""), "OK", null);
        ok.imported(UUID.randomUUID());
        assertThat(ok.getStatus().equals("IMPORTED")).isTrue();
        ComplianceImportRow bad = ComplianceImportRow.of(T, UUID.randomUUID(), 3, List.of("", "", "", ""), "REJECTED", "The name is empty");
        assertThatThrownBy(() -> bad.imported(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bad.setAside("DUPLICATE", "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deadlineRowChecksAndStoredForm() {
        assertThat(ImportRules.deadlineProblem(List.of("Acme", "VAT return", "2027-02-28", "")) == null).isTrue();
        assertThat(ImportRules.deadlineProblem(List.of("", "VAT return", "2027-02-28", "")) != null).isTrue();
        assertThat(ImportRules.deadlineProblem(List.of("Acme", "", "2027-02-28", "")) != null).isTrue();
        assertThat(ImportRules.deadlineProblem(List.of("Acme", "VAT return", "", "")) != null).isTrue();
        assertThat(ImportRules.deadlineProblem(List.of("Acme", "VAT return", "31-31-2027", "")) != null).isTrue();
        List<String> iso = ImportRules.isoDeadline(List.of("Acme", "vat return", "28/02/2027", "x"));
        assertThat(iso.get(1).equals("VAT_RETURN") && iso.get(2).equals("2027-02-28")).isTrue();
        int[] pos = ImportRules.mapHeader("DEADLINES", List.of("Client Name", "Deadline Type", "Due Date"));
        assertThat(pos[0] == 0 && pos[1] == 1 && pos[2] == 2 && pos[3] == -1).isTrue();
        assertThatThrownBy(() -> ImportRules.mapHeader("DEADLINES", List.of("Client", "Type"))).isInstanceOf(IllegalArgumentException.class);
    }
}
