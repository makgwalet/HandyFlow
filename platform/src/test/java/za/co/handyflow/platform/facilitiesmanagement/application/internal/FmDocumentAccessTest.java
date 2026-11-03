package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade.EvidenceFile;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSharingDtos.RecipientRequest;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.DocumentRef;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.ShareRef;
import za.co.handyflow.platform.secureaccess.application.SecureShareService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** FM work order files: who may see them through a secure link, and their disposal under the retention rules. */
class FmDocumentAccessTest {

    TenantId tenant = TenantId.generate();
    UUID woId = UUID.randomUUID(), clientId = UUID.randomUUID(), fileId = UUID.randomUUID();
    static final String SHA = "ab".repeat(32);

    FmWorkOrderRepository workOrders = mock(FmWorkOrderRepository.class);
    FmAssetRepository assets = mock(FmAssetRepository.class);
    FmClientRepository clients = mock(FmClientRepository.class);
    FmRecordsService records = mock(FmRecordsService.class);
    EvidenceFacade evidence = mock(EvidenceFacade.class);
    FmWorkOrder wo = mock(FmWorkOrder.class);
    FmClient client = mock(FmClient.class);

    EvidenceFile file(UUID id, String sha) { return new EvidenceFile(id, "chiller-cert.pdf", "application/pdf", 10, sha, "CERTIFICATE", "Tech", Instant.now()); }

    @BeforeEach
    void setUp() {
        when(wo.getId()).thenReturn(woId);
        when(wo.getClientId()).thenReturn(clientId);
        when(wo.getStatus()).thenReturn("COMPLETED");
        when(wo.isTerminal()).thenReturn(true);
        when(wo.getWorkOrderNumber()).thenReturn("WO-001");
        when(workOrders.findActiveById(eq(tenant), eq(woId))).thenReturn(Optional.of(wo));
        when(client.getStatus()).thenReturn("ACTIVE");
        when(clients.findActiveById(eq(tenant), eq(clientId))).thenReturn(Optional.of(client));
        when(evidence.filesFor(eq(tenant), eq("facilitiesmanagement"), eq("FmWorkOrder"), eq(woId))).thenReturn(List.of(file(fileId, SHA)));
    }

    FmShareProvider provider() { return new FmShareProvider(workOrders, clients, records, evidence); }
    ShareRef share() { return new ShareRef(UUID.randomUUID(), "FM", "WORK_ORDER", woId, clientId); }
    boolean allowed(UUID doc) { return provider().authorise(tenant.getValue(), share(), new DocumentRef("FM", "EVIDENCE", doc, null), null).allowed(); }

    @Test
    void aFileOfACompletedWorkOrderOfAnActiveClientIsReleased() {
        assertTrue(allowed(fileId));
        when(evidence.download(tenant, fileId)).thenReturn(new EvidenceFacade.DownloadedEvidence(new byte[]{1}, "chiller-cert.pdf", "application/pdf"));
        var f = provider().release(tenant.getValue(), share(), new DocumentRef("FM", "EVIDENCE", fileId, null), SHA, "Insurer");
        assertEquals("chiller-cert.pdf", f.fileName());
    }

    @Test
    void nothingIsReleasedOnceTheWorkOrderIsReopenedTheClientEndsOrTheFileGoes() {
        assertFalse(allowed(UUID.randomUUID()), "a file not attached to this work order");
        when(wo.getStatus()).thenReturn("IN_PROGRESS");
        assertFalse(allowed(fileId), "reopened");
        when(wo.getStatus()).thenReturn("COMPLETED");
        when(client.getStatus()).thenReturn("INACTIVE");
        assertFalse(allowed(fileId), "client no longer active");
        when(client.getStatus()).thenReturn("ACTIVE");
        when(wo.isFilesDisposed()).thenReturn(true);
        assertFalse(allowed(fileId), "files disposed of");
        when(wo.isFilesDisposed()).thenReturn(false);
        when(workOrders.findActiveById(eq(tenant), eq(woId))).thenReturn(Optional.empty());
        assertFalse(allowed(fileId), "work order deleted");
    }

    @Test
    void aChangedFileIsNotReleased() {
        assertThrows(IllegalStateException.class, () -> provider().release(tenant.getValue(), share(),
                new DocumentRef("FM", "EVIDENCE", fileId, null), "cd".repeat(32), "Insurer"));
        verify(evidence, never()).download(any(), any());
    }

    // ── disposal ─────────────────────────────────────────────────────────────

    @Test
    void disposalRemovesEveryFileOfTheWorkOrderAndMarksIt() {
        UUID second = UUID.randomUUID();
        when(evidence.filesFor(eq(tenant), eq("facilitiesmanagement"), eq("FmWorkOrder"), eq(woId))).thenReturn(List.of(file(fileId, SHA), file(second, SHA)));
        UUID batch = UUID.randomUUID();
        var d = new FmRetentionProvider(workOrders, assets, evidence).dispose(tenant, "WORK_ORDER", woId, batch, UUID.randomUUID());
        assertEquals(2, d.filesRemoved());
        verify(evidence).dispose(eq(tenant), eq("facilitiesmanagement"), eq("FmWorkOrder"), eq(woId), eq(fileId), contains(batch.toString()));
        verify(evidence).dispose(eq(tenant), eq("facilitiesmanagement"), eq("FmWorkOrder"), eq(woId), eq(second), anyString());
        verify(wo).markFilesDisposed(batch);
        verify(workOrders).save(wo);
    }

    @Test
    void aReopenedWorkOrderOrAnAssetBackInServiceIsNotDisposed() {
        when(wo.isTerminal()).thenReturn(false);
        when(wo.getStatus()).thenReturn("IN_PROGRESS");
        var p = new FmRetentionProvider(workOrders, assets, evidence);
        assertThrows(IllegalStateException.class, () -> p.dispose(tenant, "WORK_ORDER", woId, UUID.randomUUID(), null));
        FmAsset asset = mock(FmAsset.class);
        UUID assetId = UUID.randomUUID();
        when(asset.getStatus()).thenReturn("OPERATIONAL");
        when(assets.findActiveById(eq(tenant), eq(assetId))).thenReturn(Optional.of(asset));
        assertThrows(IllegalStateException.class, () -> p.dispose(tenant, "ASSET", assetId, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> p.dispose(tenant, "INVOICE", assetId, UUID.randomUUID(), null));
        verify(evidence, never()).dispose(any(), any(), any(), any(), any(), any());
    }

    // ── the sharing rules ────────────────────────────────────────────────────

    @Test
    void theFilesAreFixedByTheFirstPersonAndMustBelongToTheWorkOrder() {
        SecureShareService engine = mock(SecureShareService.class);
        FmWorkOrderService woService = mock(FmWorkOrderService.class);
        FmSharingService sharing = new FmSharingService(workOrders, clients, woService, records, evidence, engine);
        when(engine.findShareId(any(), any(), any(), any())).thenReturn(Optional.empty());

        HandyFlowException e = assertThrows(HandyFlowException.class, () -> sharing.addRecipient(tenant, woId,
                new RecipientRequest(List.of(UUID.randomUUID()), "Thandi", "Insurer", "t@example.com", true, true, 14, false), UUID.randomUUID(), "Me"));
        assertEquals("FILE_NOT_ATTACHED", e.getErrorCode());
        assertThrows(IllegalArgumentException.class, () -> sharing.addRecipient(tenant, woId,
                new RecipientRequest(List.of(), "Thandi", "Insurer", "t@example.com", true, true, 14, false), UUID.randomUUID(), "Me"));

        when(engine.findShareId(any(), any(), any(), any())).thenReturn(Optional.of(UUID.randomUUID()));
        HandyFlowException fixed = assertThrows(HandyFlowException.class, () -> sharing.addRecipient(tenant, woId,
                new RecipientRequest(List.of(fileId), "Sipho", null, "s@example.com", true, true, 14, false), UUID.randomUUID(), "Me"));
        assertEquals("FILES_FIXED", fixed.getErrorCode());

        when(wo.getStatus()).thenReturn("IN_PROGRESS");
        HandyFlowException open = assertThrows(HandyFlowException.class, () -> sharing.addRecipient(tenant, woId,
                new RecipientRequest(null, "Sipho", null, "s@example.com", true, true, 14, false), UUID.randomUUID(), "Me"));
        assertEquals("NOT_SHAREABLE", open.getErrorCode());
        verify(engine, never()).openShare(any());
        verify(engine, never()).addRecipient(any(), any(), any(), any(), anyBoolean(), any(), any());
    }
}
