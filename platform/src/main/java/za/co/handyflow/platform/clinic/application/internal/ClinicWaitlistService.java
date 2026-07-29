package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.model.ClinicWaitlistEntry;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicWaitlistEntryRepository;
import za.co.handyflow.platform.clinic.dto.CreateWaitlistEntryRequest;
import za.co.handyflow.platform.clinic.dto.WaitlistEntryResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicWaitlistService {

    private final ClinicWaitlistEntryRepository waitlistRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;

    @Transactional
    public WaitlistEntryResponse addToWaitlist(TenantId tenantId, CreateWaitlistEntryRequest req) {
        ClinicWaitlistEntry entry = ClinicWaitlistEntry.create(
                tenantId, req.patientId(), req.practitionerId(), req.appointmentType(), req.notes());
        waitlistRepo.save(entry);
        log.info("Added patient={} to waitlist entry={}", req.patientId(), entry.getId());
        return toResponse(entry, tenantId);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> getActiveWaitlist(TenantId tenantId) {
        List<ClinicWaitlistEntry> entries = waitlistRepo.findActive(tenantId);

        Set<UUID> patientIds = entries.stream().map(ClinicWaitlistEntry::getPatientId).collect(Collectors.toSet());
        Set<UUID> practIds = entries.stream().map(ClinicWaitlistEntry::getPractitionerId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> patientNames = patientIds.isEmpty() ? Map.of()
                : patientRepo.findAllByIds(tenantId, patientIds).stream()
                .collect(Collectors.toMap(ClinicPatient::getId, ClinicPatient::getFullName));
        Map<UUID, String> practNames = practIds.isEmpty() ? Map.of()
                : practitionerRepo.findAllByIds(tenantId, practIds).stream()
                .collect(Collectors.toMap(ClinicPractitioner::getId, ClinicPractitioner::getFullName));

        return entries.stream()
                .map(e -> new WaitlistEntryResponse(
                        e.getId(), e.getPatientId(), patientNames.getOrDefault(e.getPatientId(), "Patient"),
                        e.getPractitionerId(), e.getPractitionerId() != null ? practNames.get(e.getPractitionerId()) : null,
                        e.getAppointmentType(), e.getNotes(), e.getStatus(), e.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void markContacted(TenantId tenantId, UUID id) {
        ClinicWaitlistEntry e = find(tenantId, id);
        e.markContacted();
        waitlistRepo.save(e);
    }

    @Transactional
    public void markScheduled(TenantId tenantId, UUID id) {
        ClinicWaitlistEntry e = find(tenantId, id);
        e.markScheduled();
        waitlistRepo.save(e);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID id) {
        ClinicWaitlistEntry e = find(tenantId, id);
        e.cancel();
        waitlistRepo.save(e);
    }

    private ClinicWaitlistEntry find(TenantId tenantId, UUID id) {
        return waitlistRepo.findByIdAndTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("WaitlistEntry", id.toString()));
    }

    private WaitlistEntryResponse toResponse(ClinicWaitlistEntry e, TenantId tenantId) {
        String patientName = patientRepo.findActiveById(tenantId, e.getPatientId())
                .map(ClinicPatient::getFullName).orElse("Patient");
        String practName = e.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, e.getPractitionerId()).map(ClinicPractitioner::getFullName).orElse(null)
                : null;
        return new WaitlistEntryResponse(e.getId(), e.getPatientId(), patientName,
                e.getPractitionerId(), practName, e.getAppointmentType(), e.getNotes(),
                e.getStatus(), e.getCreatedAt());
    }
}