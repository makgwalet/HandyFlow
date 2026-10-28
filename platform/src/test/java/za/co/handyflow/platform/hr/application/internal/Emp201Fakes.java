package za.co.handyflow.platform.hr.application.internal;

import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.hr.domain.model.HrEmp201Run;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.repository.HrEmp201Repository;
import za.co.handyflow.platform.hr.domain.repository.HrEmp201RunRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayslipRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** In-memory EMP201 returns, links and pay runs, shared by the tests that need a working {@link Emp201Merge}. */
class Emp201Fakes {

    final List<HrEmp201> returns = new ArrayList<>();
    final List<HrEmp201Run> links = new ArrayList<>();
    final Map<UUID, HrPayRun> runs = new LinkedHashMap<>();
    /** ETI worked out per run, as the payslip sum would give it. */
    final Map<UUID, BigDecimal> eti = new HashMap<>();
    final Emp201Merge merge;

    Emp201Fakes() {
        merge = new Emp201Merge(returnRepo(), linkRepo(), runRepo(), payslipRepo());
    }

    void add(HrPayRun r) { runs.put(r.getId(), r); }

    List<UUID> runIdsOf(HrEmp201 e) { return links.stream().filter(l -> l.getEmp201Id().equals(e.getId())).map(HrEmp201Run::getPayRunId).toList(); }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(Emp201Fakes.class.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    static final Object NOPE = new Object();

    private HrEmp201Repository returnRepo() {
        return proxy(HrEmp201Repository.class, (n, a) -> switch (n) {
            case "save" -> { HrEmp201 e = (HrEmp201) a[0]; if (!returns.contains(e)) returns.add(e); yield e; }
            case "delete" -> { returns.remove(a[0]); yield null; }
            case "forPayRun" -> {
                Set<UUID> ids = new HashSet<>();
                for (HrEmp201Run l : links) if (l.getPayRunId().equals(a[1])) ids.add(l.getEmp201Id());
                yield returns.stream().filter(e -> ids.contains(e.getId())).toList();
            }
            case "draftForMonth" -> returns.stream().filter(e -> "DRAFT".equals(e.getStatus()) && a[1].equals(e.getTaxMonth())).toList();
            case "findByTenantAndId" -> returns.stream().filter(e -> e.getId().equals(a[1])).findFirst();
            default -> NOPE;
        });
    }

    private HrEmp201RunRepository linkRepo() {
        return proxy(HrEmp201RunRepository.class, (n, a) -> switch (n) {
            case "save" -> { links.add((HrEmp201Run) a[0]); yield a[0]; }
            case "deleteById" -> { links.removeIf(l -> l.getPayRunId().equals(a[0])); yield null; }
            case "flush" -> null;
            case "forEmp201" -> links.stream().filter(l -> l.getEmp201Id().equals(a[0])).toList();
            case "forEmp201s" -> links.stream().filter(l -> ((Collection<?>) a[0]).contains(l.getEmp201Id())).toList();
            default -> NOPE;
        });
    }

    private HrPayRunRepository runRepo() {
        return proxy(HrPayRunRepository.class, (n, a) -> switch (n) {
            case "findAllById" -> stream((Iterable<?>) a[0]).map(runs::get).filter(Objects::nonNull).toList();
            default -> NOPE;
        });
    }

    private static java.util.stream.Stream<Object> stream(Iterable<?> it) {
        List<Object> l = new ArrayList<>();
        it.forEach(l::add);
        return l.stream();
    }

    private HrPayslipRepository payslipRepo() {
        return proxy(HrPayslipRepository.class, (n, a) -> {
            if (!"sumEti".equals(n)) return NOPE;
            BigDecimal t = BigDecimal.ZERO;
            for (Object id : (Collection<?>) a[0]) t = t.add(eti.getOrDefault(id, BigDecimal.ZERO));
            return t;
        });
    }

    /** A calculated run in the given month with the given totals; tenant is used for ownership checks. */
    static HrPayRun run(TenantId tenant, String number, LocalDate start, LocalDate end, String paye, String uif, String sdl) {
        HrPayRun r = HrPayRun.create(tenant, number, start, end, end, 2027);
        r.prepared(new BigDecimal("100000"), new BigDecimal(paye), new BigDecimal(uif), new BigDecimal(sdl), new BigDecimal("80000"), 5, UUID.randomUUID());
        return r;
    }
}
