package za.co.handyflow.platform.agriculture.domain.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Paged lists need a fully ordered result: with only a date or a name, rows that tie can show on two pages or on none.
 * Every repository query that returns a Page must end its ORDER BY with the row's id.
 */
class AgPagingOrderWiringTest {

    private static final Path DIR = Path.of("src/main/java/za/co/handyflow/platform/agriculture/domain/repository");

    /** Each @Query up to the method that follows it. */
    private static final Pattern QUERY_AND_METHOD = Pattern.compile("@Query\\((.*?)\\)\\s*\\R\\s*([A-Za-z<>, ?]+?)\\s+\\w+\\(", Pattern.DOTALL);
    private static final Pattern ORDER_BY = Pattern.compile("ORDER BY ([^\"]*)\"");

    static List<String> pagedQueriesWithoutIdTiebreak(String source) {
        List<String> bad = new ArrayList<>();
        Matcher m = QUERY_AND_METHOD.matcher(source);
        while (m.find()) {
            if (!m.group(2).startsWith("Page<")) continue;
            Matcher o = ORDER_BY.matcher(m.group(1));
            String last = null;
            while (o.find()) last = o.group(1);
            if (last == null || !Pattern.compile("(^|,)\\s*\\w+\\.id( DESC| ASC)?\\s*$").matcher(last).find()) bad.add(m.group(1).strip());
        }
        return bad;
    }

    static int pagedQueries(String source) {
        int n = 0;
        Matcher m = QUERY_AND_METHOD.matcher(source);
        while (m.find()) if (m.group(2).startsWith("Page<")) n++;
        return n;
    }

    @Test void everyPagedQueryEndsWithTheId() throws IOException {
        int seen = 0;
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith("Repository.java")).toList()) {
                String code = Files.readString(f);
                seen += pagedQueries(code);
                assertEquals(List.of(), pagedQueriesWithoutIdTiebreak(code), f.getFileName() + ": paged query without an id tie-break");
            }
        }
        assertTrue(seen >= 40, "the scan found the paged queries (" + seen + ")");
    }

    @Test void theScanCatchesAMissingTiebreak() {
        String bad = "@Query(\"SELECT e FROM X e WHERE e.a = :a ORDER BY e.eventDate DESC\")\n    Page<X> find(@Param(\"a\") String a, Pageable p);";
        String good = "@Query(\"SELECT e FROM X e WHERE e.a = :a ORDER BY e.eventDate DESC, e.id DESC\")\n    Page<X> find(@Param(\"a\") String a, Pageable p);";
        String list = "@Query(\"SELECT e FROM X e ORDER BY e.eventDate DESC\")\n    List<X> all();";
        assertEquals(1, pagedQueriesWithoutIdTiebreak(bad).size());
        assertEquals(0, pagedQueriesWithoutIdTiebreak(good).size());
        assertEquals(0, pagedQueriesWithoutIdTiebreak(list).size());
    }
}
