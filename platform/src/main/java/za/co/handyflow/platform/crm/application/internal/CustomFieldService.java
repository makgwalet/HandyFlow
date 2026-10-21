package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomField;
import za.co.handyflow.platform.crm.domain.model.CustomFieldType;
import za.co.handyflow.platform.crm.domain.repository.CustomFieldRepository;
import za.co.handyflow.platform.crm.dto.CustomFieldRequest;
import za.co.handyflow.platform.crm.dto.CustomFieldResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Each account's own customer fields, and the checking of values entered against them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldService {

    private final CustomFieldRepository repository;

    @Transactional(readOnly = true)
    public List<CustomFieldResponse> list(TenantId tenantId, boolean includeInactive) {
        var rows = includeInactive ? repository.findAllForTenant(tenantId) : repository.findActiveForTenant(tenantId);
        return rows.stream().map(CustomFieldService::toResponse).toList();
    }

    @Transactional
    public CustomFieldResponse create(TenantId tenantId, CustomFieldRequest req) {
        List<String> options = CustomFieldRules.cleanOptions(req.options());
        String problem = CustomFieldRules.definitionProblem(req.label(), req.type(), options);
        if (problem != null) throw new IllegalArgumentException(problem);
        var all = repository.findAllForTenant(tenantId);
        if (all.size() >= CustomFieldRules.MAX_FIELDS) {
            throw new IllegalArgumentException("An account can have at most " + CustomFieldRules.MAX_FIELDS + " custom fields. Switch one off instead.");
        }
        Set<String> keys = new HashSet<>();
        all.forEach(f -> keys.add(f.getKey()));
        int order = req.sortOrder() != null ? req.sortOrder() : all.size();
        var field = CustomField.create(tenantId, CustomFieldRules.keyFor(req.label(), keys), req.label(), req.type(),
                Boolean.TRUE.equals(req.required()), options, order);
        repository.save(field);
        log.info("[CRM] Custom field created key={} type={} tenant={}", field.getKey(), field.getType(), tenantId);
        return toResponse(field);
    }

    @Transactional
    public CustomFieldResponse update(TenantId tenantId, UUID id, CustomFieldRequest req) {
        var field = repository.findByIdAndTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CustomField", id.toString()));
        List<String> options = field.getType() == CustomFieldType.CHOICE
                ? CustomFieldRules.cleanOptions(req.options() != null ? req.options() : field.getOptions()) : List.of();
        String label = req.label() != null ? req.label() : field.getLabel();
        String problem = CustomFieldRules.definitionProblem(label, field.getType(), options);
        if (problem != null) throw new IllegalArgumentException(problem);
        field.update(label, req.required() != null ? req.required() : field.isRequired(), options,
                req.sortOrder() != null ? req.sortOrder() : field.getSortOrder(), req.active() != null ? req.active() : field.isActive());
        repository.save(field);
        return toResponse(field);
    }

    /** Checks the entered values against the active fields. See CustomFieldRules.clean for the rules. */
    @Transactional(readOnly = true)
    public Map<String, String> clean(TenantId tenantId, Map<String, String> input, Map<String, String> existing) {
        var defs = repository.findActiveForTenant(tenantId).stream()
                .map(f -> new CustomFieldRules.Def(f.getKey(), f.getLabel(), f.getType(), f.isRequired(), f.getOptions() == null ? List.of() : f.getOptions()))
                .toList();
        return CustomFieldRules.clean(defs, input, existing);
    }

    private static CustomFieldResponse toResponse(CustomField f) {
        return new CustomFieldResponse(f.getId(), f.getKey(), f.getLabel(), f.getType(), f.isRequired(),
                f.getOptions() == null ? List.of() : f.getOptions(), f.getSortOrder(), f.isActive());
    }
}
