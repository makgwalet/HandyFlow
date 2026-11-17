package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelCompartmentCleaning;
import za.co.handyflow.platform.fuel.domain.repository.FuelCompartmentCleaningRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankerCompartmentRepository;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.CleaningView;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.StoredFile;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.Upload;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.UUID;

/**
 * Cleaning certificates: a scan or photo of the certificate for a compartment cleaning, kept in the shared file store with the same checks as the other
 * Fuel evidence (type decided from the bytes: JPEG, PNG, WebP or PDF; size capped). One file per cleaning, never replaced. A second person verifies it.
 * Whether a cleaning needs a verified certificate to clear a changeover is the company's choice in Settings (see {@link FuelEligibilityService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelCleaningCertificateService {

    private final FuelCompartmentCleaningRepository cleanings;
    private final FuelTankerCompartmentRepository tankerCompartments;
    private final FileStorageService storage;

    @Transactional
    public CleaningView attach(TenantId tenantId, UUID tankerId, UUID compartmentId, UUID cleaningId, String filename, byte[] bytes, UUID by) {
        FuelCompartmentCleaning c = find(tenantId, tankerId, compartmentId, cleaningId);
        if (c.hasFile()) throw new IllegalStateException("This cleaning already has a certificate. Record a new cleaning to correct it.");
        String type = FuelEvidenceFileService.validate(new Upload("DOCUMENT", filename, bytes, null, null));
        String name = FuelEvidenceFileService.safeName(filename, type);
        String key;
        try {
            key = storage.store("fuel/cleaning/" + tenantId + "/" + cleaningId, name, type, bytes);
        } catch (IOException ex) {
            log.warn("Could not store cleaning certificate for {}", cleaningId, ex);
            throw new IllegalStateException("The file could not be saved. Try again.");
        }
        try {
            c.attachFile(key, type, bytes.length, name, by);
            return FuelEligibilityService.cleaningView(cleanings.save(c));
        } catch (RuntimeException ex) {
            try { storage.delete(key); } catch (IOException | RuntimeException ignored) { log.warn("Could not remove orphaned cleaning file {}", key); }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public StoredFile download(TenantId tenantId, UUID tankerId, UUID compartmentId, UUID cleaningId) {
        FuelCompartmentCleaning c = find(tenantId, tankerId, compartmentId, cleaningId);
        if (!c.hasFile()) throw new ResourceNotFoundException("Cleaning certificate", cleaningId.toString());
        try {
            return new StoredFile(storage.retrieve(c.getFileRef()), c.getContentType(), c.getFileName() == null ? "certificate" : c.getFileName());
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read cleaning certificate for {}", cleaningId, ex);
            throw new IllegalStateException("The stored file could not be read.");
        }
    }

    /** A second person confirms the certificate (the entity and the database both refuse the person who recorded the cleaning or uploaded the file). */
    @Transactional
    public CleaningView verify(TenantId tenantId, UUID tankerId, UUID compartmentId, UUID cleaningId, UUID by) {
        FuelCompartmentCleaning c = find(tenantId, tankerId, compartmentId, cleaningId);
        c.verify(by);
        return FuelEligibilityService.cleaningView(cleanings.save(c));
    }

    private FuelCompartmentCleaning find(TenantId tenantId, UUID tankerId, UUID compartmentId, UUID cleaningId) {
        if (tankerCompartments.findByTanker(tenantId, tankerId).stream().noneMatch(k -> k.getId().equals(compartmentId))) throw new ResourceNotFoundException("Compartment", compartmentId.toString());
        FuelCompartmentCleaning c = cleanings.findOne(tenantId, cleaningId).orElseThrow(() -> new ResourceNotFoundException("Cleaning", cleaningId.toString()));
        if (!c.getCompartmentId().equals(compartmentId)) throw new ResourceNotFoundException("Cleaning", cleaningId.toString());
        return c;
    }
}
