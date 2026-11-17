package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelImportKey;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.fuel.domain.model.FuelImportPumpAlias;
import za.co.handyflow.platform.fuel.domain.model.FuelPump;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportKeyRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPumpRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Phase 8e (D03): inbound pump/POS shift import, and only that. A sender posts finished shifts with its per-company key; each is staged for review
 * and changes nothing. A supervisor accepts it, and accepting runs the ordinary open-shift and close-shift code (so meter, tender, variance and
 * stock rules are the same as typing the shift in). There is no device, vendor or outbound code here.
 * <ul>
 *   <li>Idempotent: source + externalId is unique per company. The same shift sent again with the same content returns the original; with different
 *       content it is refused (409), never overwritten. To correct a shift, send it under a new externalId.</li>
 *   <li>Shape is checked on receipt; business problems (unknown pump, pump already on an open shift) are listed on the staged import and checked again on accept.</li>
 *   <li>Shifts created by accept have no opener (the import is the opener), so the separate-closer rule is met by the supervisor who accepts.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPosImportService {

    private static final String KEY_PREFIX = "hf_fuel_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FuelImportKeyRepository keys;
    private final FuelPosImportRepository imports;
    private final FuelPumpRepository pumps;
    private final za.co.handyflow.platform.fuel.domain.repository.FuelImportPumpAliasRepository aliases;
    private final FuelForecourtService forecourt;
    private final FuelDivisionGuard divisions;
    private final FeatureGuard featureGuard;
    private final ObjectMapper mapper;

    // ── keys ─────────────────────────────────────────────────────────────────

    @Transactional
    public CreatedKey createKey(TenantId tenantId, UUID by, String name) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String apiKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        FuelImportKey key = keys.save(FuelImportKey.create(tenantId, name.trim(), hashKey(apiKey), apiKey.substring(0, 12), by));
        log.info("Fuel import key created id={} tenant={}", key.getId(), tenantId);
        return new CreatedKey(view(key), apiKey);
    }

    @Transactional(readOnly = true)
    public List<KeyView> listKeys(TenantId tenantId) { return keys.findAllForTenant(tenantId).stream().map(FuelPosImportService::view).toList(); }

    @Transactional
    public KeyView revokeKey(TenantId tenantId, UUID id) {
        FuelImportKey k = keys.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("FuelImportKey", id.toString()));
        k.revoke();
        log.info("Fuel import key revoked id={} tenant={}", id, tenantId);
        return view(keys.save(k));
    }

    private static KeyView view(FuelImportKey k) { return new KeyView(k.getId(), k.getName(), k.getKeyPrefix(), k.getCreatedAt(), k.getLastUsedAt(), k.getRevokedAt()); }

    /** The same answer for a missing, unknown or revoked key, so a caller learns nothing about which. */
    FuelImportKey authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required");
        FuelImportKey key = keys.findByKeyHash(hashKey(rawKey.trim())).filter(FuelImportKey::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required"));
        key.touch(Instant.now());
        return key;
    }

    static String hashKey(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    // ── ingest ───────────────────────────────────────────────────────────────

    @Transactional
    public IngestResult ingest(String rawKey, IncomingShift in) {
        FuelImportKey key = authenticate(rawKey);
        TenantId tenantId = key.getTenantId();
        if (!featureGuard.hasModuleFor(tenantId, "fuel")) throw new HandyFlowException("Fuel is not available for this company.", HttpStatus.FORBIDDEN, "FUEL_MODULE_UNAVAILABLE");
        divisions.require(tenantId, Set.of(FuelDivision.RETAIL_FORECOURT));
        checkShape(in);

        String source = in.source().trim(), ref = in.externalId().trim();
        String hash = hash(canonical(in));
        Optional<FuelPosImport> existing = imports.findByRef(tenantId, source, ref);
        if (existing.isPresent()) {
            FuelPosImport e = existing.get();
            if (!e.getPayloadHash().equals(hash)) {
                throw new HandyFlowException("A shift with this source and externalId was already received with different content. Send a correction under a new externalId.",
                        HttpStatus.CONFLICT, "FUEL_IMPORT_CONTENT_CHANGED");
            }
            return new IngestResult(e.getId(), e.getStatus(), true, problemList(e.getProblems()));
        }
        List<String> problems = problemsFor(tenantId, in);
        FuelPosImport staged = imports.save(FuelPosImport.receive(tenantId, key.getId(), source, ref, json(in), hash, String.join("\n", problems)));
        log.info("Fuel POS import staged id={} tenant={} source={} problems={}", staged.getId(), tenantId, source, problems.size());
        return new IngestResult(staged.getId(), staged.getStatus(), false, problems);
    }

    /** Shape rules the annotations cannot express. Throws 400 and stages nothing. */
    static void checkShape(IncomingShift in) {
        Set<String> names = new HashSet<>();
        for (IncomingReading r : in.readings()) {
            if (!names.add(r.pumpName().trim().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Pump " + r.pumpName().trim() + " appears twice.");
            if (r.closingMeter().compareTo(r.openingMeter()) < 0) throw new IllegalArgumentException("The closing meter for " + r.pumpName().trim() + " is below its opening meter.");
            BigDecimal test = r.testLitres() == null ? BigDecimal.ZERO : r.testLitres();
            if (test.compareTo(r.closingMeter().subtract(r.openingMeter())) > 0) throw new IllegalArgumentException("Test litres for " + r.pumpName().trim() + " are more than the meter moved.");
        }
        if (in.openedAt() != null && in.closedAt() != null && in.closedAt().isBefore(in.openedAt())) throw new IllegalArgumentException("The shift closes before it opens.");
    }

    /** One fixed text for the content, so 10 and 10.00 are the same and the order of readings does not matter. */
    static String canonical(IncomingShift in) {
        StringBuilder sb = new StringBuilder();
        sb.append(in.source().trim()).append('|').append(in.externalId().trim()).append('|').append(in.attendantName().trim()).append('|')
          .append(in.openedAt()).append('|').append(in.closedAt()).append('|')
          .append(n(in.cash())).append('|').append(n(in.card())).append('|').append(n(in.account())).append('|').append(n(in.other())).append('|').append(n(in.fleetCard())).append('|')
          .append(in.notes() == null ? "" : in.notes().trim());
        in.readings().stream().sorted(Comparator.comparing(r -> r.pumpName().trim().toLowerCase(Locale.ROOT)))
                .forEach(r -> sb.append('|').append(r.pumpName().trim().toLowerCase(Locale.ROOT)).append(':').append(n(r.openingMeter())).append(':').append(n(r.closingMeter())).append(':').append(n(r.testLitres())));
        return sb.toString();
    }

    private static String n(BigDecimal v) { return v == null ? "0" : v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString(); }

    private static String hash(String s) { return hashKey(s); }

    private List<String> problemsFor(TenantId tenantId, IncomingShift in) {
        Map<String, FuelPump> byName = pumpsFor(tenantId, in.source());
        List<String> problems = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (IncomingReading r : in.readings()) {
            FuelPump p = byName.get(r.pumpName().trim().toLowerCase(Locale.ROOT));
            if (p == null) problems.add("Pump \"" + r.pumpName().trim() + "\" is not set up in Fuel. Add it, or map it under Imported shifts > Pump labels.");
            else if (!used.add(p.getId())) problems.add("Two labels in this shift point to the same pump (" + p.getName() + ").");
        }
        return problems;
    }

    /** Pumps by the label a source may send: the Fuel pump name, overridden by any alias set for that source. */
    private Map<String, FuelPump> pumpsFor(TenantId tenantId, String source) {
        Map<String, FuelPump> m = new HashMap<>();
        Map<UUID, FuelPump> byId = new HashMap<>();
        for (FuelPump p : pumps.findAllActive(tenantId)) { m.put(p.getName().trim().toLowerCase(Locale.ROOT), p); byId.put(p.getId(), p); }
        for (var a : aliases.findBySource(tenantId, FuelImportPumpAlias.normalise(source))) {
            FuelPump p = byId.get(a.getPumpId());
            if (p != null) m.put(a.getAlias(), p); // an alias pointing at a pump that is no longer active is ignored
        }
        return m;
    }

    // ── pump labels ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AliasView> listAliases(TenantId tenantId) {
        Map<UUID, String> names = new HashMap<>();
        for (FuelPump p : pumps.findAllActive(tenantId)) names.put(p.getId(), p.getName());
        List<AliasView> out = new ArrayList<>();
        for (var a : aliases.findAllForTenant(tenantId)) out.add(new AliasView(a.getId(), a.getSource(), a.getAlias(), a.getPumpId(), names.getOrDefault(a.getPumpId(), "Inactive pump"), a.getCreatedAt()));
        return out;
    }

    @Transactional
    public AliasView addAlias(TenantId tenantId, UUID by, AliasRequest req) {
        FuelPump pump = pumps.findAllActive(tenantId).stream().filter(p -> p.getId().equals(req.pumpId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pump", String.valueOf(req.pumpId())));
        var x = FuelImportPumpAlias.of(tenantId, req.source(), req.alias(), pump.getId(), by);
        if (aliases.findOne(tenantId, x.getSource(), x.getAlias()).isPresent()) throw new IllegalStateException("That label is already mapped for this source.");
        aliases.save(x);
        return new AliasView(x.getId(), x.getSource(), x.getAlias(), pump.getId(), pump.getName(), x.getCreatedAt());
    }

    @Transactional
    public void removeAlias(TenantId tenantId, UUID id) {
        aliases.delete(aliases.findById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Pump label", id.toString())));
    }

    // ── review ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ImportView> list(TenantId tenantId, String status, int limit) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !Set.of("PENDING", "ACCEPTED", "REJECTED").contains(s)) throw new IllegalArgumentException("Status must be PENDING, ACCEPTED or REJECTED.");
        return imports.findByStatus(tenantId, s, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ImportView get(TenantId tenantId, UUID id) { return toView(find(tenantId, id)); }

    /** Opens and closes a shift from the import using the normal code. Any refusal (meters, tenders, variance) rolls everything back and leaves the import pending. */
    @Transactional
    public ImportView accept(TenantId tenantId, UUID id, AcceptRequest req, UUID by) {
        FuelPosImport imp = find(tenantId, id);
        if (!imp.isPending()) throw new IllegalStateException("This import is already " + imp.getStatus().toLowerCase() + ".");
        IncomingShift in = parse(imp);
        Map<String, FuelPump> byName = pumpsFor(tenantId, imp.getSource());
        List<OpenReading> open = new ArrayList<>();
        List<CloseReading> close = new ArrayList<>();
        for (IncomingReading r : in.readings()) {
            FuelPump p = byName.get(r.pumpName().trim().toLowerCase(Locale.ROOT));
            if (p == null) throw new IllegalArgumentException("Pump \"" + r.pumpName().trim() + "\" is not set up in Fuel. Add it under Forecourt > Pumps, or map it under Imported shifts > Pump labels, then accept again.");
            open.add(new OpenReading(p.getId(), r.openingMeter()));
            close.add(new CloseReading(p.getId(), r.closingMeter(), r.testLitres()));
        }
        String notes = ("Imported from " + imp.getSource() + " ref " + imp.getExternalId()
                + (in.openedAt() != null ? ", opened " + in.openedAt() : "") + (in.closedAt() != null ? ", closed " + in.closedAt() : "")
                + (in.notes() == null || in.notes().isBlank() ? "" : ". " + in.notes().trim()));
        if (notes.length() > 500) notes = notes.substring(0, 500);
        ShiftResponse opened = forecourt.openShift(tenantId, new OpenShiftRequest(in.attendantName().trim(), notes, open), null);
        forecourt.closeShift(tenantId, opened.id(),
                new CloseShiftRequest(in.cash(), in.card(), in.account(), in.other(), req == null ? null : req.varianceReason(), close, in.fleetCard()), by);
        if (in.openedAt() != null || in.closedAt() != null) forecourt.recordProviderTimes(tenantId, opened.id(), in.openedAt(), in.closedAt());
        imp.accept(by, opened.id(), req == null ? null : req.note());
        imports.save(imp);
        log.info("Fuel POS import accepted id={} shift={} by={}", id, opened.id(), by);
        return toView(imp);
    }

    @Transactional
    public ImportView reject(TenantId tenantId, UUID id, String reason, UUID by) {
        FuelPosImport imp = find(tenantId, id);
        imp.reject(by, reason);
        return toView(imports.save(imp));
    }

    private FuelPosImport find(TenantId tenantId, UUID id) {
        return imports.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("FuelPosImport", id.toString()));
    }

    private ImportView toView(FuelPosImport i) {
        return new ImportView(i.getId(), i.getSource(), i.getExternalId(), i.getStatus(), i.getReceivedAt(), problemList(i.getProblems()), parse(i),
                i.getDecidedBy(), i.getDecidedAt(), i.getDecisionNote(), i.getShiftId());
    }

    private static List<String> problemList(String s) { return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split("\n")); }

    private String json(IncomingShift in) {
        try { return mapper.writeValueAsString(in); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not store the shift", e); }
    }

    private IncomingShift parse(FuelPosImport i) {
        try { return mapper.readValue(i.getPayload(), IncomingShift.class); } catch (JsonProcessingException e) { throw new IllegalStateException("Stored shift could not be read", e); }
    }
}
