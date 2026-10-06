package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStoreTenderPackageStorageTest {

    private final FileStorageService files = mock(FileStorageService.class);
    private final FileStoreTenderPackageStorage storage = new FileStoreTenderPackageStorage(files);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    @Test
    @DisplayName("store keeps the bytes under a tenant/tender/package prefix and returns the opaque key, size and content hash")
    void stores() throws Exception {
        UUID tender = UUID.randomUUID();
        UUID pkg = UUID.randomUUID();
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        when(files.store(any(), any(), any(), any())).thenReturn("key-1");

        TenderPackageStorage.Stored stored = storage.store(tenant, tender, pkg, "pack.pdf", "application/pdf", content);

        assertThat(stored.storageKey()).isEqualTo("key-1");
        assertThat(stored.sizeBytes()).isEqualTo(3L);
        assertThat(stored.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        verify(files).store(eq("tender-packages/" + tenant.getValue() + "/" + tender + "/" + pkg), eq("pack.pdf"), eq("application/pdf"), eq(content));
    }

    @Test
    @DisplayName("load and delete pass the key through unchanged")
    void loadsAndDeletes() throws Exception {
        when(files.retrieve("key-1")).thenReturn(new byte[]{1, 2});
        assertThat(storage.load("key-1").length).isEqualTo(2);
        storage.delete("key-1");
        verify(files).delete("key-1");
    }

    @Test
    @DisplayName("a storage failure surfaces as an unchecked exception, not a swallowed error")
    void failure() throws Exception {
        when(files.store(any(), any(), any(), any())).thenThrow(new IOException("disk full"));
        assertThatThrownBy(() -> storage.store(tenant, UUID.randomUUID(), UUID.randomUUID(), "p.pdf", "application/pdf", new byte[]{1}))
                .isInstanceOf(UncheckedIOException.class);
    }
}
