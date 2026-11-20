package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The capped list reads must really carry a page of RowLimit.MAX_ROWS, and the plain reads that summaries and sweeps
 * use must stay unbounded. Checked on each repository by calling its default methods on a proxy that records the Pageable
 * the underlying query would receive. No database needed.
 */
class RowLimitTest {

    private static final List<Class<?>> CAPPED = List.of(
            IncidentRepository.class, PaiaRequestRepository.class, CapaRepository.class, AuditFindingRepository.class,
            FindingRepository.class, ControlTestRepository.class, CoiDeclarationRepository.class, GiftEntryRepository.class,
            RiskRepository.class);

    private static Object[] blankArgs(Class<?>[] types) {
        Object[] a = new Object[types.length];
        for (int i = 0; i < types.length; i++) if (types[i] == boolean.class) a[i] = false;
        return a;
    }

    /** Calls the named zero-risk default method; returns the Pageable the abstract search received. */
    private static Pageable pageSeen(Class<?> repo, String method) throws Exception {
        List<Pageable> seen = new ArrayList<>();
        InvocationHandler h = (proxy, m, args) -> {
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            if (m.getName().equals("search")) { seen.add((Pageable) args[args.length - 1]); return List.of(); }
            throw new UnsupportedOperationException(m.getName());
        };
        Object p = Proxy.newProxyInstance(repo.getClassLoader(), new Class<?>[]{repo}, h);
        Method d = null;
        for (Method m : repo.getMethods()) if (m.getName().equals(method) && m.isDefault()) d = m;
        assertNotNull(d, repo.getSimpleName() + " has no default " + method);
        d.invoke(p, blankArgs(d.getParameterTypes()));
        assertEquals(1, seen.size(), repo.getSimpleName() + "." + method + " must make exactly one query");
        return seen.get(0);
    }

    @Test
    void cappedReadsAskForAtMostMaxRowsFromTheFirstPage() throws Exception {
        assertEquals(500, RowLimit.MAX_ROWS);
        for (Class<?> repo : CAPPED) {
            Pageable p = pageSeen(repo, "searchCapped");
            assertTrue(p.isPaged(), repo.getSimpleName() + " capped read must be paged");
            assertEquals(0, p.getPageNumber(), repo.getSimpleName());
            assertEquals(RowLimit.MAX_ROWS, p.getPageSize(), repo.getSimpleName());
        }
    }

    @Test
    void plainReadsStayUnbounded() throws Exception {
        for (Class<?> repo : CAPPED) {
            assertTrue(pageSeen(repo, "search").isUnpaged(), repo.getSimpleName() + " summaries and sweeps must see every row");
        }
    }
}
