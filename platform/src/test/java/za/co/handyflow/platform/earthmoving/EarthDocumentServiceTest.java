package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.documents.application.DocumentsFacade.FileUpload;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EarthDocumentServiceTest {
    DocumentsFacade documents = mock(DocumentsFacade.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    UserRecipientResolver users = mock(UserRecipientResolver.class);
    EarthAlertService alerts = mock(EarthAlertService.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthDocumentService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    EarthAsset asset;
    FileUpload file = new FileUpload("a.pdf", "application/pdf", new byte[]{1});

    DocumentInfo doc(String category, LocalDate expires, String ownerType) {
        return new DocumentInfo(UUID.randomUUID(), ownerType, asset.getId(), category, "Doc", null, expires, "ACTIVE", null, 1, null, Instant.now(), actor);
    }

    @BeforeEach
    void setUp() {
        service = new EarthDocumentService(documents, assets, users, alerts, access);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null, "OWN", null, null, null, null, null, null);
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
    }

    @Test void aManualNeedsWriteButInsuranceNeedsAdmin() {
        granted.add("EARTHMOVING_WRITE");
        service.upload(tenant, "ASSET", asset.getId(), "MANUAL", "Manual", null, null, file, actor);
        verify(documents).upload(eq(tenant), eq("EARTH_ASSET"), eq(asset.getId()), eq("MANUAL"), any(), any(), any(), any(), eq(actor));
        assertThrows(HandyFlowException.class, () -> service.upload(tenant, "ASSET", asset.getId(), "INSURANCE", "Policy", null, LocalDate.now().plusYears(1), file, actor));
        granted.add("EARTHMOVING_ADMIN");
        service.upload(tenant, "ASSET", asset.getId(), "INSURANCE", "Policy", null, LocalDate.now().plusYears(1), file, actor);
    }

    @Test void insuranceWithoutAnExpiryDateIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.upload(tenant, "ASSET", asset.getId(), "INSURANCE", "Policy", null, null, file, actor));
        verify(documents, never()).upload(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void aDocumentForAnUnknownMachineOrOperatorIsRefused() {
        granted.add("EARTHMOVING_WRITE");
        assertThrows(ResourceNotFoundException.class, () -> service.upload(tenant, "ASSET", UUID.randomUUID(), "PHOTO", "Front", null, null, file, actor));
        granted.add("EARTHMOVING_ADMIN");
        when(users.resolveUser(any(), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.upload(tenant, "OPERATOR", UUID.randomUUID(), "OPERATOR_CERTIFICATE", "Licence", null, LocalDate.now().plusYears(1), file, actor));
    }

    @Test void theListHidesSensitiveDocumentsFromPeopleWhoCannotSeeThem() {
        granted.add("EARTHMOVING_READ");
        DocumentInfo manual = doc("MANUAL", null, "EARTH_ASSET"), policy = doc("INSURANCE", LocalDate.now().plusDays(10), "EARTH_ASSET");
        when(documents.list(tenant, "EARTH_ASSET", asset.getId(), false)).thenReturn(List.of(manual, policy));
        assertEquals(List.of(manual), service.list(tenant, "ASSET", asset.getId(), false));
        granted.add("EARTHMOVING_ADMIN");
        assertEquals(2, service.list(tenant, "ASSET", asset.getId(), false).size());
    }

    @Test void anotherModulesDocumentIsNotFoundHere() {
        granted.add("EARTHMOVING_READ");
        DocumentInfo foreign = doc("MANUAL", null, "EXPENSE_CLAIM");
        when(documents.find(tenant, foreign.id())).thenReturn(Optional.of(foreign));
        assertThrows(ResourceNotFoundException.class, () -> service.download(tenant, foreign.id(), null, actor));
        assertThrows(ResourceNotFoundException.class, () -> service.versions(tenant, foreign.id()));
    }

    @Test void downloadingInsuranceNeedsAdmin() {
        granted.add("EARTHMOVING_READ");
        DocumentInfo policy = doc("INSURANCE", LocalDate.now().plusDays(10), "EARTH_ASSET");
        when(documents.find(tenant, policy.id())).thenReturn(Optional.of(policy));
        assertThrows(HandyFlowException.class, () -> service.download(tenant, policy.id(), null, actor));
        verify(documents, never()).download(any(), any(), any(), any());
    }

    @Test void renewingADocumentSettlesTheAlertsRaisedForTheOldDate() {
        granted.add("EARTHMOVING_ADMIN");
        LocalDate old = LocalDate.now().plusDays(5), renewed = old.plusYears(1);
        DocumentInfo before = doc("INSURANCE", old, "EARTH_ASSET");
        DocumentInfo after = new DocumentInfo(before.id(), before.ownerType(), before.ownerId(), "INSURANCE", "Doc", null, renewed, "ACTIVE", null, 2, null, Instant.now(), actor);
        when(documents.find(tenant, before.id())).thenReturn(Optional.of(before));
        when(documents.addVersion(tenant, before.id(), file, "Renewed", renewed, actor)).thenReturn(after);
        service.addVersion(tenant, before.id(), file, "Renewed", renewed, actor);
        verify(alerts, times(3)).resolveByKey(eq(tenant), argThat(k -> k.startsWith("DOC_EXPIRY:" + before.id() + ":" + old)), eq(actor), any());
    }

    @Test void aVersionWithTheSameExpiryLeavesTheAlertsAlone() {
        granted.add("EARTHMOVING_WRITE");
        DocumentInfo before = doc("MANUAL", null, "EARTH_ASSET");
        when(documents.find(tenant, before.id())).thenReturn(Optional.of(before));
        when(documents.addVersion(any(), any(), any(), any(), any(), any())).thenReturn(before);
        service.addVersion(tenant, before.id(), file, null, null, actor);
        verify(alerts, never()).resolveByKey(any(), any(), any(), any());
    }

    @Test void archivingNeedsAdminAndSettlesAlerts() {
        granted.add("EARTHMOVING_WRITE");
        DocumentInfo d = doc("INSURANCE", LocalDate.now().plusDays(3), "EARTH_ASSET");
        assertThrows(HandyFlowException.class, () -> service.archive(tenant, d.id(), "Cancelled policy", actor));
        granted.add("EARTHMOVING_ADMIN");
        when(documents.find(tenant, d.id())).thenReturn(Optional.of(d));
        when(documents.archive(tenant, d.id(), "Cancelled policy", actor)).thenReturn(d);
        service.archive(tenant, d.id(), "Cancelled policy", actor);
        verify(alerts, times(3)).resolveByKey(eq(tenant), any(), eq(actor), any());
    }
}
