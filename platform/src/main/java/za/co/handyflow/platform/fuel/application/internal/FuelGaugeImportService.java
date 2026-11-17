package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelGaugeReading;
import za.co.handyflow.platform.fuel.domain.model.FuelImportKey;
import za.co.handyflow.platform.fuel.domain.model.FuelImportTankAlias;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelGaugeReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportTankAliasRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.DipReadingRequest;
import za.co.handyflow.platform.fuel.dto.DipReadingResponse;
import za.co.handyflow.platform.fuel.dto.GaugeImportDtos.*;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IngestResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Tank-gauge feed. A gauge or tank-monitoring system sends a tank's level with the company's import key (the same keys as the pump/POS import). Nothing
 * changes stock: the reading is staged, a person accepts it, and acceptance records an ordinary dip reading through FuelService, so variance, the
 * negative-variance alert and the audit trail are exactly those of typing the dip in. Idempotent per source and externalId; the same id with different
 * content is refused. The tank is found by the sender's label: a label set for that source, otherwise the Fuel tank name.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelGaugeImportService {

    /** A reading stamped further ahead than this is refused; a small allowance covers clock drift between the gauge and this server. */
    private static final Duration CLOCK_DRIFT = Duration.ofMinutes(10);

    private final FuelPosImportService keyAuth;
    private final FuelGaugeReadingRepository readings;
    private final FuelImportTankAliasRepository aliases;
    private final FuelTankRepository tanks;
    private final FuelService fuel;
    private final FeatureGuard featureGuard;

    // ── ingest ───────────────────────────────────────────────────────────────

    @Transactional
    public IngestResult ingest(String rawKey, IncomingGauge in) {
        FuelImportKey key = keyAuth.authenticate(rawKey);
        TenantId tenantId = key.getTenantId();
        if (!featureGuard.hasModuleFor(tenantId, "fuel")) throw new HandyFlowException("Fuel is not available for this company.", HttpStatus.FORBIDDEN, "FUEL_MODULE_UNAVAILABLE");
        if (in.readAt().isAfter(Instant.now().plus(CLOCK_DRIFT))) throw new IllegalArgumentException("The reading time is in the future.");
        String source = in.source().trim(), ref = in.externalId().trim(), label = in.tank().trim();
        String hash = hash(source, ref, label.toLowerCase(Locale.ROOT), in.litres().stripTrailingZeros().toPlainString(), in.readAt().toString());
        Optional<FuelGaugeReading> existing = readings.findByRef(tenantId, source, ref);
        if (existing.isPresent()) {
            FuelGaugeReading e = existing.get();
            if (!e.getPayloadHash().equals(hash)) {
                throw new HandyFlowException("A reading with this source and externalId was already received with different content. Send a correction under a new externalId.",
                        HttpStatus.CONFLICT, "FUEL_IMPORT_CONTENT_CHANGED");
            }
            return new IngestResult(e.getId(), e.getStatus(), true, problemList(e.getProblems()));
        }
        List<String> problems = new ArrayList<>();
        if (resolve(tenantId, source, label) == null) problems.add("Tank \"" + label + "\" is not set up in Fuel. Add it, or map it under Gauge readings > Tank labels.");
        FuelGaugeReading staged = readings.save(FuelGaugeReading.receive(tenantId, key.getId(), source, ref, label, in.litres(), in.readAt(), in.senderName(), hash, String.join("\n", problems)));
        log.info("Fuel gauge reading staged id={} tenant={} source={} problems={}", staged.getId(), tenantId, source, problems.size());
        return new IngestResult(staged.getId(), staged.getStatus(), false, problems);
    }

    // ── review ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GaugeView> list(TenantId tenantId, String status, int limit) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !Set.of("PENDING", "ACCEPTED", "REJECTED").contains(s)) throw new IllegalArgumentException("Status must be PENDING, ACCEPTED or REJECTED.");
        Map<UUID, FuelTank> byId = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) byId.put(t.getId(), t);
        List<GaugeView> out = new ArrayList<>();
        for (FuelGaugeReading g : readings.findByStatus(tenantId, s, PageRequest.of(0, Math.max(1, Math.min(limit, 200))))) {
            FuelTank t = resolve(tenantId, g.getSource(), g.getTankLabel(), byId);
            out.add(view(g, t));
        }
        return out;
    }

    /** Records a dip reading from the staged level through the normal path. A refusal rolls everything back and leaves the reading pending. */
    @Transactional
    public GaugeView accept(TenantId tenantId, UUID id, AcceptRequest req, UUID by) {
        FuelGaugeReading g = find(tenantId, id);
        if (!g.isPending()) throw new IllegalStateException("This reading is already " + g.getStatus().toLowerCase() + ".");
        FuelTank t = resolve(tenantId, g.getSource(), g.getTankLabel());
        if (t == null) throw new IllegalArgumentException("Tank \"" + g.getTankLabel() + "\" is not set up in Fuel. Add it, or map it under Gauge readings > Tank labels, then accept again.");
        String who = g.getSenderName() != null ? g.getSenderName() : "Gauge: " + g.getSource();
        DipReadingResponse dip = fuel.recordDipReading(tenantId, t.getId(),
                new DipReadingRequest(g.getLitres(), g.getReadAt(), who.length() > 100 ? who.substring(0, 100) : who, "Imported from " + g.getSource() + " ref " + g.getExternalId()));
        g.accept(by, dip.id(), req == null ? null : req.note());
        readings.save(g);
        log.info("Fuel gauge reading accepted id={} dip={} by={}", id, dip.id(), by);
        return view(g, t);
    }

    @Transactional
    public GaugeView reject(TenantId tenantId, UUID id, String reason, UUID by) {
        FuelGaugeReading g = find(tenantId, id);
        g.reject(by, reason);
        readings.save(g);
        return view(g, resolve(tenantId, g.getSource(), g.getTankLabel()));
    }

    // ── tank labels ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AliasView> listAliases(TenantId tenantId) {
        Map<UUID, String> names = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) names.put(t.getId(), t.getName());
        List<AliasView> out = new ArrayList<>();
        for (var a : aliases.findAllForTenant(tenantId)) out.add(new AliasView(a.getId(), a.getSource(), a.getAlias(), a.getTankId(), names.getOrDefault(a.getTankId(), "Inactive tank"), a.getCreatedAt()));
        return out;
    }

    @Transactional
    public AliasView addAlias(TenantId tenantId, UUID by, AliasRequest req) {
        FuelTank tank = tanks.findActiveById(tenantId, req.tankId()).orElseThrow(() -> new ResourceNotFoundException("Tank", req.tankId().toString()));
        var x = FuelImportTankAlias.of(tenantId, req.source(), req.alias(), tank.getId(), by);
        if (aliases.findOne(tenantId, x.getSource(), x.getAlias()).isPresent()) throw new IllegalStateException("That label is already mapped for this source.");
        aliases.save(x);
        return new AliasView(x.getId(), x.getSource(), x.getAlias(), tank.getId(), tank.getName(), x.getCreatedAt());
    }

    @Transactional
    public void removeAlias(TenantId tenantId, UUID id) {
        aliases.delete(aliases.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Tank label", id.toString())));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FuelTank resolve(TenantId tenantId, String source, String label) {
        Map<UUID, FuelTank> byId = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) byId.put(t.getId(), t);
        return resolve(tenantId, source, label, byId);
    }

    /** The Fuel tank a label means for a source: a label set for that source first, otherwise the tank name. */
    private FuelTank resolve(TenantId tenantId, String source, String label, Map<UUID, FuelTank> byId) {
        String l = FuelImportTankAlias.normalise(label);
        for (var a : aliases.findBySource(tenantId, FuelImportTankAlias.normalise(source))) {
            if (a.getAlias().equals(l) && byId.containsKey(a.getTankId())) return byId.get(a.getTankId());
        }
        for (FuelTank t : byId.values()) if (t.getName() != null && t.getName().trim().toLowerCase(Locale.ROOT).equals(l)) return t;
        return null;
    }

    private FuelGaugeReading find(TenantId tenantId, UUID id) {
        return readings.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Gauge reading", id.toString()));
    }

    private static GaugeView view(FuelGaugeReading g, FuelTank t) {
        return new GaugeView(g.getId(), g.getSource(), g.getExternalId(), g.getStatus(), g.getReceivedAt(), problemList(g.getProblems()), g.getTankLabel(),
                t == null ? null : t.getId(), t == null ? null : t.getName(), g.getLitres(), g.getReadAt(), g.getSenderName(), g.getDecidedBy(), g.getDecidedAt(), g.getDecisionNote(), g.getDipReadingId());
    }

    private static List<String> problemList(String s) { return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split("\n")); }

    private static String hash(String... parts) { return FuelPosImportService.hashKey(String.join("|", parts)); }
}
