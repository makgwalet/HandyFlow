package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.TenderLookupValue;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderLookupValueRepository;
import za.co.handyflow.platform.compliancetender.dto.TenderLookupValueResponse;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A company's own additions to the pick-lists on the compliance and tender screens. Additions only extend the built-in lists; they never replace them. */
@Service
@RequiredArgsConstructor
public class TenderLookupService {

    /** The lists a company may extend: the names the screens use. */
    public static final Set<String> LIST_KEYS = Set.of("DOCUMENT_TYPES", "DEADLINE_TYPES", "TENDER_AUTHORITIES", "INDUSTRIES",
            "APPLIES_TO", "CLASS_OF_WORK", "PERSONNEL_ROLES", "UNITS");

    private final TenderLookupValueRepository values;

    /** Trims and collapses inner spaces; a value is stored the way it will be shown. */
    public static String clean(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    @Transactional(readOnly = true)
    public List<TenderLookupValueResponse> list(TenantId tenantId) {
        return values.findAllForTenant(tenantId).stream().map(TenderLookupService::toResponse).toList();
    }

    /** Adding a value that is already there (any capitalisation) returns the existing one, so a double click is harmless. */
    @Transactional
    public TenderLookupValueResponse add(TenantId tenantId, String listKey, String rawValue, UUID by) {
        if (!LIST_KEYS.contains(listKey)) throw new BusinessException("Unknown list: " + listKey);
        String value = clean(rawValue);
        if (value.isEmpty()) throw new BusinessException("Enter a value to add.");
        if (value.length() > 100) throw new BusinessException("A value can have at most 100 characters.");
        return toResponse(values.findByValue(tenantId, listKey, value)
                .orElseGet(() -> values.save(TenderLookupValue.create(tenantId, listKey, value, by))));
    }

    @Transactional
    public void remove(TenantId tenantId, UUID id) {
        values.delete(values.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("TenderLookupValue", id.toString())));
    }

    private static TenderLookupValueResponse toResponse(TenderLookupValue v) {
        return new TenderLookupValueResponse(v.getId(), v.getListKey(), v.getValue());
    }
}
