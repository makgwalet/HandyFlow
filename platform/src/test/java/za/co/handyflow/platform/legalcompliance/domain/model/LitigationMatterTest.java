package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LitigationMatterTest {

    private final TenantId tenantId = TenantId.generate();

    private LitigationMatter newMatter() {
        return LitigationMatter.create(tenantId, "LM-00001", "Dispute over unpaid invoice",
                LitigationMatterType.COMMERCIAL, "Acme Supplies (Pty) Ltd", "CLAIMANT",
                new BigDecimal("150000.00"), "Smith & Associates", "Johannesburg Magistrate's Court",
                "CASE/2026/001", LocalDate.now(), LocalDate.now().plusDays(30), "Unpaid invoice dispute",
                UUID.randomUUID());
    }

    @Test
    @DisplayName("create() starts OPEN")
    void createStartsOpen() {
        LitigationMatter m = newMatter();
        assertEquals(LitigationStatus.OPEN, m.getStatus());
        assertEquals("LM-00001", m.getMatterNumber());
    }

    @Test
    @DisplayName("advanceStatus() moves to a non-terminal status while open")
    void advanceStatusWorksWhileOpen() {
        LitigationMatter m = newMatter();
        m.advanceStatus(LitigationStatus.IN_PROGRESS);
        assertEquals(LitigationStatus.IN_PROGRESS, m.getStatus());
    }

    @Test
    @DisplayName("close() requires a terminal status")
    void closeRejectsNonTerminalStatus() {
        LitigationMatter m = newMatter();
        assertThrows(IllegalArgumentException.class, () -> m.close(LitigationStatus.IN_PROGRESS, "not terminal"));
    }

    @Test
    @DisplayName("close() with SETTLED sets closedDate and outcome notes")
    void closeWithSettledSucceeds() {
        LitigationMatter m = newMatter();
        m.close(LitigationStatus.SETTLED, "Settled out of court for R100,000");
        assertEquals(LitigationStatus.SETTLED, m.getStatus());
        assertEquals(LocalDate.now(), m.getClosedDate());
        assertEquals("Settled out of court for R100,000", m.getOutcomeNotes());
    }

    @Test
    @DisplayName("update() throws once the matter is closed")
    void updateThrowsWhenClosed() {
        LitigationMatter m = newMatter();
        m.close(LitigationStatus.WITHDRAWN, "Claim withdrawn");
        assertThrows(IllegalStateException.class, () -> m.update("New title", "New party", "DEFENDANT",
                BigDecimal.TEN, "Rep", "Forum", "Ref", LocalDate.now(), "desc"));
    }

    @Test
    @DisplayName("advanceStatus() throws once the matter is closed")
    void advanceStatusThrowsWhenClosed() {
        LitigationMatter m = newMatter();
        m.close(LitigationStatus.CLOSED, null);
        assertThrows(IllegalStateException.class, () -> m.advanceStatus(LitigationStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("linkContract() sets the linked contract id")
    void linkContractSetsId() {
        LitigationMatter m = newMatter();
        UUID contractId = UUID.randomUUID();
        m.linkContract(contractId);
        assertEquals(contractId, m.getLinkedContractId());
    }

    @Test
    @DisplayName("softDelete() marks the matter deleted")
    void softDeleteMarksDeleted() {
        LitigationMatter m = newMatter();
        assertFalse(m.isDeleted());
        m.softDelete(UUID.randomUUID());
        assertTrue(m.isDeleted());
    }
}
