package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;
import za.co.handyflow.platform.contracting.domain.repository.ContractPartyRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractSignatureRepository;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.Decision;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.DocumentRef;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.ShareRef;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The signed contract as a record: executed copy, sharing decisions and disposal. */
class ContractRecordsTest {

    TenantId tenant = TenantId.generate();
    static final String SHA = "ab".repeat(32);

    Contract signed() {
        Contract c = Contract.create(tenant, "CT-007", "Cleaning services", "SERVICE_AGREEMENT", "<p>Terms</p>", null, UUID.randomUUID());
        c.send();
        c.markSigned();
        return c;
    }

    // ── the record itself ────────────────────────────────────────────────────

    @Test
    void theExecutedCopyIsRecordedOnceAndOnlyForASignedContract() {
        Contract draft = Contract.create(tenant, "CT-1", "Draft", "NDA", "x", null, UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> draft.recordExecutedCopy("k", SHA, 10));
        Contract c = signed();
        c.recordExecutedCopy("contracting/k1", SHA, 10);
        assertTrue(c.hasExecutedCopy());
        assertThrows(IllegalStateException.class, () -> c.recordExecutedCopy("contracting/k2", SHA, 10));
        assertEquals("contracting/k1", c.getExecutedStorageKey());
    }

    @Test
    void onlyAContractThatHasEndedCanBeDisposedOfAndItsTextIsReplaced() {
        Contract c = signed();
        assertThrows(IllegalStateException.class, () -> c.dispose(UUID.randomUUID()), "in force");
        c.terminate("Client went out of business");
        assertTrue(c.isEnded());
        UUID batch = UUID.randomUUID();
        c.dispose(batch);
        assertTrue(c.isDisposed());
        assertEquals(batch, c.getDisposalBatchId());
        assertTrue(c.getBody().contains("disposed of under the records retention policy"));
        assertFalse(c.getBody().contains("Terms"));
        assertThrows(IllegalStateException.class, () -> c.dispose(UUID.randomUUID()));
    }

    @Test
    void theRetentionClockStartsFromTerminationThenEndDate() {
        // dates can only be set while the contract is a draft, so set them before it is sent and signed
        Contract c = Contract.create(tenant, "CT-008", "Cleaning services", "SERVICE_AGREEMENT", "<p>Terms</p>", null, UUID.randomUUID());
        c.setDates(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31));
        c.send();
        c.markSigned();
        assertEquals(LocalDate.of(2027, 12, 31), c.endedOn(), "the end date until it is terminated");
        c.terminate("Mutual agreement");
        assertEquals(LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")), c.endedOn());
    }

    @Test
    void disposalClearsTheDrawnSignatureAndDeviceButKeepsWhenItWasSigned() {
        ContractSignature s = ContractSignature.create(tenant, UUID.randomUUID(), UUID.randomUUID(), "otp-hash", "1234", "10.0.0.1",
                "Firefox", "data:image/png;base64,AAAA", null);
        s.scrubForDisposal();
        assertNull(s.getSignatureData());
        assertNull(s.getIpAddress());
        assertNull(s.getUserAgent());
        assertEquals("1234", s.getPhoneLast4());
        assertNotNull(s.getSignedAt());
    }

    // ── sharing ──────────────────────────────────────────────────────────────

    ContractRepository contracts = mock(ContractRepository.class);
    ContractPartyRepository parties = mock(ContractPartyRepository.class);
    ContractSignatureRepository signatures = mock(ContractSignatureRepository.class);
    ContractRecordsService records = mock(ContractRecordsService.class);
    DocumentStore store = mock(DocumentStore.class);

    ContractingShareProvider shareProvider() { return new ContractingShareProvider(contracts, parties, records); }

    Decision ask(Contract c) {
        ShareRef share = new ShareRef(UUID.randomUUID(), "CONTRACTING", "CONTRACT", c.getId(), c.getId());
        return shareProvider().authorise(tenant.getValue(), share, new DocumentRef("CONTRACTING", "EXECUTED_CONTRACT", c.getId(), "executed"), null);
    }

    @Test
    void aSignedContractWithItsExecutedCopyIsReleasedAndNothingElse() {
        Contract c = signed();
        when(contracts.findActiveById(eq(tenant), eq(c.getId()))).thenReturn(Optional.of(c));
        assertFalse(ask(c).allowed(), "no stored copy yet");
        c.recordExecutedCopy("contracting/k1", SHA, 10);
        assertTrue(ask(c).allowed());

        ShareRef share = new ShareRef(UUID.randomUUID(), "CONTRACTING", "CONTRACT", c.getId(), c.getId());
        assertFalse(shareProvider().authorise(tenant.getValue(), share, new DocumentRef("CONTRACTING", "EXECUTED_CONTRACT", UUID.randomUUID(), "executed"), null).allowed(),
                "another contract's id under this share");
        assertFalse(shareProvider().authorise(tenant.getValue(), share, new DocumentRef("PROJECTS", "DOCUMENT", c.getId(), null), null).allowed());

        when(records.executedCopy(eq(tenant), eq(c))).thenReturn(new byte[]{1, 2, 3});
        var f = shareProvider().release(tenant.getValue(), share, new DocumentRef("CONTRACTING", "EXECUTED_CONTRACT", c.getId(), "executed"), SHA, "Bank");
        assertEquals("CT-007.pdf", f.fileName());
        assertEquals("application/pdf", f.contentType());
        assertThrows(IllegalStateException.class, () -> shareProvider().release(tenant.getValue(), share,
                new DocumentRef("CONTRACTING", "EXECUTED_CONTRACT", c.getId(), "executed"), "cd".repeat(32), "Bank"), "a different issued hash");
    }

    @Test
    void aDisposedOrDeletedContractIsNeverReleased() {
        Contract c = signed();
        c.recordExecutedCopy("contracting/k1", SHA, 10);
        when(contracts.findActiveById(eq(tenant), eq(c.getId()))).thenReturn(Optional.empty());
        assertFalse(ask(c).allowed());
        when(contracts.findActiveById(eq(tenant), eq(c.getId()))).thenReturn(Optional.of(c));
        c.terminate("Ended");
        c.dispose(UUID.randomUUID());
        assertFalse(ask(c).allowed());
    }

    // ── disposal ─────────────────────────────────────────────────────────────

    @Test
    void disposalDeletesTheExecutedCopyClearsSignaturesAndKeepsTheRecord() throws Exception {
        Contract c = signed();
        c.recordExecutedCopy("contracting/k1", SHA, 10);
        c.terminate("Ended");
        ContractSignature s = ContractSignature.create(tenant, c.getId(), UUID.randomUUID(), "h", "1234", "10.0.0.1", "Firefox", "data:x", null);
        when(contracts.findForUpdate(tenant.getValue(), c.getId())).thenReturn(Optional.of(c));
        when(signatures.findByContract(c.getId())).thenReturn(List.of(s));
        var p = new ContractingRetentionProvider(contracts, signatures, store);
        UUID batch = UUID.randomUUID();
        var d = p.dispose(tenant, "CONTRACT", c.getId(), batch, UUID.randomUUID());
        assertEquals(1, d.filesRemoved());
        assertTrue(d.detail().contains(SHA));
        verify(store).delete(eq(tenant), eq("contracting/k1"), contains(batch.toString()));
        assertTrue(c.isDisposed());
        assertNull(s.getSignatureData());
        verify(contracts).save(c);
    }

    @Test
    void aContractInForceAgainIsNotDisposed() throws Exception {
        Contract c = signed();
        when(contracts.findForUpdate(tenant.getValue(), c.getId())).thenReturn(Optional.of(c));
        var p = new ContractingRetentionProvider(contracts, signatures, store);
        assertThrows(IllegalStateException.class, () -> p.dispose(tenant, "CONTRACT", c.getId(), UUID.randomUUID(), UUID.randomUUID()));
        verify(store, never()).delete(any(), any(), any());
        assertFalse(c.isDisposed());
    }

    @Test
    void aDisposedContractCannotBeDownloaded() {
        assertEquals("DISPOSED", ContractRecordsService.disposed().getErrorCode());
        assertInstanceOf(HandyFlowException.class, ContractRecordsService.disposed());
        assertEquals("CT_007_A.pdf", ContractRecordsService.fileName(Contract.create(tenant, "CT/007 A", "t", "NDA", "x", null, UUID.randomUUID())));
    }
}
