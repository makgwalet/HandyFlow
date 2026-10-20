package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Technician;
import za.co.handyflow.platform.fleet.domain.repository.TechnicianRepository;
import za.co.handyflow.platform.fleet.dto.SaveTechnicianRequest;
import za.co.handyflow.platform.fleet.dto.TechnicianResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnicianService {

    private final TechnicianRepository technicianRepository;

    @Transactional(readOnly = true)
    public List<TechnicianResponse> list(TenantId tenantId, boolean activeOnly) {
        List<Technician> all = activeOnly
                ? technicianRepository.findActiveForTenant(tenantId)
                : technicianRepository.findAllForTenant(tenantId);
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TechnicianResponse create(TenantId tenantId, SaveTechnicianRequest req) {
        Technician t = Technician.create(tenantId, req.name().trim(), blankToNull(req.phone()),
                blankToNull(req.email()), req.hourlyRate());
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public TechnicianResponse update(TenantId tenantId, UUID id, SaveTechnicianRequest req) {
        Technician t = technicianRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician", id.toString()));
        t.update(req.name().trim(), blankToNull(req.phone()), blankToNull(req.email()), req.hourlyRate(),
                req.active() == null || req.active());
        technicianRepository.save(t);
        return toResponse(t);
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private TechnicianResponse toResponse(Technician t) {
        return new TechnicianResponse(t.getId(), t.getName(), t.getPhone(), t.getEmail(), t.getHourlyRate(), t.isActive());
    }
}