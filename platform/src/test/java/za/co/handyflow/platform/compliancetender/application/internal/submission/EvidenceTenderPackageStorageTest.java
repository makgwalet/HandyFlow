package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade.DownloadedEvidence;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceTenderPackageStorageTest {

    private final EvidenceFacade evidence = mock(EvidenceFacade.class);
    private final EvidenceTenderPackageStorage storage = new EvidenceTenderPackageStorage(evidence);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    @Test
    @DisplayName("store hands the bytes to Evidence tagged as a tender package and returns the stored id and content hash")
    void stores() throws Exception {
        UUID packageId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        EvidenceResponse response = new EvidenceResponse(evidenceId, "pack.pdf", "application/pdf", 3, "TENDER_PACKAGE", "ACTIVE", "Sam", Instant.now());
        when(evidence.attach(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(response);

        TenderPackageStorage.Stored stored = storage.store(tenant, packageId, "pack.pdf", "application/pdf", content, UUID.randomUUID(), "Sam");

        assertThat(stored.storageId()).isEqualTo(evidenceId);
        assertThat(stored.sizeBytes()).isEqualTo(3L);
        assertThat(stored.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(evidence).attach(eq(tenant), file.capture(), eq("TENDER_PACKAGE"), eq("compliancetender"), eq("TenderPackage"),
                eq(packageId), eq(null), any(), eq("Sam"));
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("pack.pdf");
        assertThat(file.getValue().getBytes()).isEqualTo(content);
        assertThat(file.getValue().isEmpty()).isEqualTo(false);
    }

    @Test
    @DisplayName("load returns the downloaded bytes with their file name and type")
    void loads() {
        UUID id = UUID.randomUUID();
        when(evidence.download(tenant, id)).thenReturn(new DownloadedEvidence(new byte[]{1, 2}, "pack.pdf", "application/pdf"));

        TenderPackageStorage.Loaded loaded = storage.load(tenant, id);

        assertThat(loaded.fileName()).isEqualTo("pack.pdf");
        assertThat(loaded.content().length).isEqualTo(2);
    }
}
