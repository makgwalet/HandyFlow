package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackageDraft;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageDraftRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.dto.TenderPackageDraftResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/** Keeps what a person has entered on the package screen, so a reload or a colleague opening the tender does not start from nothing. */
@Service
@RequiredArgsConstructor
public class TenderPackageDraftService {

    private final TenderPackageDraftRepository drafts;
    private final TenderRepository tenders;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Optional<TenderPackageDraftResponse> find(TenantId tenantId, UUID tenderId) {
        requireTender(tenantId, tenderId);
        return drafts.findByTender(tenantId, tenderId).map(this::toResponse);
    }

    @Transactional
    public TenderPackageDraftResponse save(TenantId tenantId, UUID tenderId, JsonNode data, UUID userId, String userName) {
        requireTender(tenantId, tenderId);
        if (data == null || !data.isObject()) throw new IllegalArgumentException("The draft must be an object.");
        String json = data.toString();
        TenderPackageDraft draft = drafts.findByTender(tenantId, tenderId).orElse(null);
        if (draft == null) draft = TenderPackageDraft.create(tenantId, tenderId, json, userId, userName);
        else draft.replace(json, userId, userName);
        return toResponse(drafts.save(draft));
    }

    @Transactional
    public void discard(TenantId tenantId, UUID tenderId) {
        requireTender(tenantId, tenderId);
        drafts.findByTender(tenantId, tenderId).ifPresent(drafts::delete);
    }

    private void requireTender(TenantId tenantId, UUID tenderId) {
        tenders.findByIdForTenant(tenantId, tenderId).orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
    }

    private TenderPackageDraftResponse toResponse(TenderPackageDraft d) {
        try {
            return new TenderPackageDraftResponse(mapper.readTree(d.getData()), d.getUpdatedAt(), d.getUpdatedByName());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The saved draft could not be read.", e);
        }
    }
}
