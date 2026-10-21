package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueMediaServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller USER = new Caller(UUID.randomUUID(), false, false, false);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5, 6, 7, 8};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', 0, 0, 0, 0};

    @Mock CatalogueItemMediaRepository media;
    @Mock CatalogueItemRepository items;
    @Mock CatalogueAuditEventRepository audit;
    @Mock FileStorageService storage;
    CatalogueMediaService service;
    CatalogueItem item;

    @BeforeEach
    void setUp() throws Exception {
        service = new CatalogueMediaService(media, items, audit, storage);
        item = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Brick", null, "B-1", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("5.00"), null, new BigDecimal("15.00"), false), CatalogueItemStatus.ACTIVE);
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        when(media.save(any())).thenAnswer(i -> i.getArgument(0));
        when(media.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(media.primaryOf(TENANT, item.getId())).thenReturn(Optional.empty());
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("key-1");
    }

    @Test
    void firstPictureBecomesTheMainPictureAndIsAudited() throws Exception {
        var out = service.upload(TENANT, item.getId(), "front.png", "image/png", PNG, " Front ", null, USER);
        assertThat(out.primary()).isTrue();
        assertThat(out.kind()).isEqualTo("IMAGE");
        assertThat(out.title()).isEqualTo("Front");
        assertThat(out.url()).isEqualTo("/api/v1/catalogue/items/" + item.getId() + "/media/" + out.id() + "/file");
        verify(storage).store("catalogue/" + TENANT.getValue() + "/" + item.getId(), "front.png", "image/png", PNG);
        verify(audit).save(any());
    }

    @Test
    void laterPicturesAreNotMainAndDocumentsNeverAre() {
        when(media.primaryOf(TENANT, item.getId())).thenReturn(Optional.of(CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.IMAGE,
                null, null, "a.png", "image/png", 10, "k", true, 0, null)));
        assertThat(service.upload(TENANT, item.getId(), "b.png", "image/png", PNG, null, null, USER).primary()).isFalse();
        assertThat(service.upload(TENANT, item.getId(), "spec.pdf", "application/pdf", PDF, null, null, USER).primary()).isFalse();
    }

    @Test
    void aFileThatIsNotWhatItSaysIsRefusedAndNothingIsStored() throws Exception {
        assertThatThrownBy(() -> service.upload(TENANT, item.getId(), "x.png", "image/png", PDF, null, null, USER)).hasMessageContaining("does not match");
        assertThatThrownBy(() -> service.upload(TENANT, item.getId(), "x.svg", "image/svg+xml", PNG, null, null, USER)).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> service.upload(TENANT, item.getId(), "x.png", "image/png", new byte[0], null, null, USER)).hasMessageContaining("non-empty");
        verify(storage, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    void sizeAndCountLimitsApply() throws Exception {
        byte[] big = new byte[(int) MediaRules.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> service.upload(TENANT, item.getId(), "big.png", "image/png", big, null, null, USER)).hasMessageContaining("too large");
        when(media.count(TENANT, item.getId(), MediaRules.Kind.IMAGE)).thenReturn((long) MediaRules.MAX_IMAGES_PER_ITEM);
        assertThatThrownBy(() -> service.upload(TENANT, item.getId(), "c.png", "image/png", PNG, null, null, USER)).hasMessageContaining("at most 12");
        verify(storage, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    void makingAnotherPictureMainFreesTheOldOneFirst() {
        var old = CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.IMAGE, null, null, "a.png", "image/png", 10, "k1", true, 0, null);
        var next = CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.IMAGE, null, null, "b.png", "image/png", 10, "k2", false, 1, null);
        when(media.primaryOf(TENANT, item.getId())).thenReturn(Optional.of(old));
        when(media.findInItem(TENANT, item.getId(), next.getId())).thenReturn(Optional.of(next));
        var out = service.update(TENANT, item.getId(), next.getId(), new za.co.handyflow.platform.catalogue.dto.MediaUpdateRequest(null, "A brick", true), USER);
        assertThat(out.primary()).isTrue();
        assertThat(old.isPrimary()).isFalse();
        assertThat(out.altText()).isEqualTo("A brick");
        verify(media).saveAndFlush(old);
    }

    @Test
    void aDocumentCannotBeMadeTheMainPicture() {
        var doc = CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.DOCUMENT, null, null, "s.pdf", "application/pdf", 10, "k", false, 0, null);
        when(media.findInItem(TENANT, item.getId(), doc.getId())).thenReturn(Optional.of(doc));
        assertThatThrownBy(() -> service.update(TENANT, item.getId(), doc.getId(), new za.co.handyflow.platform.catalogue.dto.MediaUpdateRequest(null, null, true), USER))
                .hasMessageContaining("Only a picture");
    }

    @Test
    void removingTheMainPictureHandsItToTheNextOne() {
        var main = CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.IMAGE, null, null, "a.png", "image/png", 10, "k1", true, 0, null);
        var other = CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.IMAGE, null, null, "b.png", "image/png", 10, "k2", false, 1, null);
        when(media.findInItem(TENANT, item.getId(), main.getId())).thenReturn(Optional.of(main));
        when(media.forItem(TENANT, item.getId())).thenReturn(List.of(other));
        service.remove(TENANT, item.getId(), main.getId(), USER);
        assertThat(main.isDeleted()).isTrue();
        assertThat(main.isPrimary()).isFalse();
        assertThat(other.isPrimary()).isTrue();
    }

    @Test
    void downloadReturnsTheStoredBytesAndAnUnknownFileIsNotFound() throws Exception {
        var m = CatalogueItemMedia.create(TENANT, item.getId(), MediaRules.Kind.IMAGE, null, null, "a.png", "image/png", 12, "k1", true, 0, null);
        when(media.findInItem(TENANT, item.getId(), m.getId())).thenReturn(Optional.of(m));
        when(storage.retrieve("k1")).thenReturn(PNG);
        var dl = service.download(TENANT, item.getId(), m.getId());
        assertThat(dl.fileName()).isEqualTo("a.png");
        assertThat(dl.bytes().length).isEqualTo(12);
        assertThatThrownBy(() -> service.download(TENANT, item.getId(), UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }
}
