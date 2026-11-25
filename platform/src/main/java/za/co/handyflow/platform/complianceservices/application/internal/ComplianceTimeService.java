package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.TimeDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/** Recording hours worked. Hours only: nothing here knows what anyone costs. */
@Service
@RequiredArgsConstructor
public class ComplianceTimeService {

    static final int MAX_RANGE_DAYS = 366;

    private final ComplianceTimeEntryRepository entries;
    private final ComplianceClientRepository clients;

    @Transactional
    public EntryView record(TenantId tenantId, RecordRequest r, UUID userId, boolean admin, LocalDate today) {
        String clientName = clientName(tenantId, r.clientId());
        int already = (int) entries.minutesOnDay(tenantId, userId, r.workDate());
        ComplianceTimeEntry e = guard(() -> ComplianceTimeEntry.record(tenantId, r.clientId(), userId, r.workDate(), r.minutes(), r.serviceCode(), r.kind(),
                r.billable() == null || r.billable(), r.note(), today, admin, already));
        return view(entries.save(e), clientName);
    }

    @Transactional(readOnly = true)
    public List<EntryView> mine(TenantId tenantId, UUID userId, LocalDate from, LocalDate to) {
        range(from, to);
        Map<UUID, String> names = names(tenantId);
        List<EntryView> out = new ArrayList<>();
        for (ComplianceTimeEntry e : entries.findForUser(tenantId, userId, from, to)) out.add(view(e, names.get(e.getClientId())));
        return out;
    }

    @Transactional(readOnly = true)
    public List<EntryView> forClient(TenantId tenantId, UUID clientId, LocalDate from, LocalDate to) {
        range(from, to);
        String name = clientName(tenantId, clientId);
        List<EntryView> out = new ArrayList<>();
        for (ComplianceTimeEntry e : entries.findForClient(tenantId, clientId, from, to)) out.add(view(e, name));
        return out;
    }

    /** Your own hours between two days, by client and by day. Voided entries do not count. */
    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId, UUID userId, LocalDate from, LocalDate to) {
        range(from, to);
        Map<UUID, String> names = names(tenantId);
        Map<String, TimeRules.Totals> byClient = new LinkedHashMap<>();
        Map<String, TimeRules.Totals> byDay = new TreeMap<>();
        TimeRules.Totals all = TimeRules.Totals.none();
        for (ComplianceTimeEntry e : entries.findForUser(tenantId, userId, from, to)) {
            if (!e.isActive()) continue;
            all = all.plus(e.getMinutes(), e.isBillable(), e.getKind());
            byClient.merge(e.getClientId().toString(), TimeRules.Totals.none().plus(e.getMinutes(), e.isBillable(), e.getKind()), TimeRules.Totals::add);
            byDay.merge(e.getWorkDate().toString(), TimeRules.Totals.none().plus(e.getMinutes(), e.isBillable(), e.getKind()), TimeRules.Totals::add);
        }
        List<Bucket> clientBuckets = new ArrayList<>();
        byClient.forEach((id, t) -> clientBuckets.add(bucket(id, names.getOrDefault(UUID.fromString(id), "Client"), t)));
        clientBuckets.sort((a, b) -> Integer.compare(b.minutes(), a.minutes()));
        List<Bucket> dayBuckets = new ArrayList<>();
        byDay.forEach((d, t) -> dayBuckets.add(bucket(d, d, t)));
        return new Summary(from, to, all.minutes(), all.billableMinutes(), all.reworkMinutes(), all.unplannedMinutes(), clientBuckets, dayBuckets);
    }

    @Transactional
    public EntryView voidEntry(TenantId tenantId, UUID id, UUID by, boolean admin, String reason) {
        ComplianceTimeEntry e = entries.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceTimeEntry", id.toString()));
        if (!admin && !e.getUserId().equals(by)) throw new HandyFlowException("You can only void your own time", HttpStatus.FORBIDDEN, "TIME_NOT_YOURS");
        guard(() -> { e.voidIt(by, reason); return null; });
        return view(entries.save(e), clientName(tenantId, e.getClientId()));
    }

    private static Bucket bucket(String key, String label, TimeRules.Totals t) {
        return new Bucket(key, label, t.minutes(), t.billableMinutes(), t.reworkMinutes(), t.unplannedMinutes());
    }

    private static EntryView view(ComplianceTimeEntry e, String clientName) {
        return new EntryView(e.getId(), e.getClientId(), clientName, e.getUserId(), e.getWorkDate(), e.getMinutes(), e.getServiceCode(), e.getKind(), e.isBillable(), e.getNote(),
                e.getStatus(), e.getVoidReason(), e.getCreatedAt());
    }

    static void range(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new HandyFlowException("Choose a start day that is not after the end day", HttpStatus.BAD_REQUEST, "TIME_RANGE");
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) throw new HandyFlowException("Choose a period of a year or less", HttpStatus.BAD_REQUEST, "TIME_RANGE");
    }

    private Map<UUID, String> names(TenantId tenantId) {
        Map<UUID, String> out = new HashMap<>();
        for (ComplianceClient c : clients.findAll(tenantId, null, Pageable.unpaged()).getContent()) out.put(c.getId(), c.getName());
        return out;
    }

    private String clientName(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).map(ComplianceClient::getName).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TIME_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TIME_INVALID");
        }
    }
}
