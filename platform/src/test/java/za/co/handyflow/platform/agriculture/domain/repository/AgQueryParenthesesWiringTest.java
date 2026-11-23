package za.co.handyflow.platform.agriculture.domain.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A @Query with a stray or missing bracket is only found when Spring builds the repository, which stops the whole application
 * from starting. This reads every @Query in the agriculture repositories and checks its brackets balance.
 */
class AgQueryParenthesesWiringTest {

    private static final Path DIR = Path.of("src/main/java/za/co/handyflow/platform/agriculture/domain/repository");
    /** The text of each @Query: the string literals (plain or text block) inside the annotation's own brackets. */
    static List<String> queries(String source) {
        List<String> out = new ArrayList<>();
        int from = 0;
        while (true) {
            int at = source.indexOf("@Query(", from);
            if (at < 0) return out;
            int i = at + "@Query(".length(), depth = 1;
            StringBuilder text = new StringBuilder();
            while (i < source.length() && depth > 0) {
                if (source.startsWith("\"\"\"", i)) {
                    int end = source.indexOf("\"\"\"", i + 3);
                    text.append(source, i + 3, end).append(' ');
                    i = end + 3;
                } else if (source.charAt(i) == '"') {
                    int end = i + 1;
                    while (source.charAt(end) != '"') end += source.charAt(end) == '\\' ? 2 : 1;
                    text.append(source, i + 1, end).append(' ');
                    i = end + 1;
                } else {
                    char c = source.charAt(i);
                    if (c == '(') depth++;
                    if (c == ')') depth--;
                    i++;
                }
            }
            out.add(text.toString().strip());
            from = i;
        }
    }

    /** Brackets outside quoted text, which may hold a bracket of its own. Returns the depth at the end, or -1 if it ever went negative. */
    static int depthAtEnd(String query) {
        String s = query.replaceAll("'[^']*'", "''");
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') { depth--; if (depth < 0) return -1; }
        }
        return depth;
    }

    static int count(String source) { return queries(source).size(); }

    static List<String> unbalanced(String source) {
        List<String> bad = new ArrayList<>();
        for (String q : queries(source)) if (depthAtEnd(q) != 0) bad.add(q);
        return bad;
    }

    @Test void everyQueryHasBalancedBrackets() throws IOException {
        int seen = 0;
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith("Repository.java")).toList()) {
                String code = Files.readString(f);
                seen += count(code);
                assertEquals(List.of(), unbalanced(code), f.getFileName() + ": a @Query whose brackets do not balance");
            }
        }
        assertTrue(seen >= 100, "the scan found the queries (" + seen + ")");
    }

    @Test void theScanCatchesAnExtraBracket() {
        String bad = "@Query(\"SELECT r FROM X r WHERE r.id IN (SELECT c.id FROM Y c WHERE c.a IN (SELECT 1 FROM Z z)))\")\n    List<X> find(int a);";
        assertEquals(1, unbalanced(bad).size());
    }

    @Test void theScanCatchesAMissingBracket() {
        String bad = "@Query(\"SELECT r FROM X r WHERE r.id IN (SELECT c.id FROM Y c\")\n    List<X> find(int a);";
        assertEquals(1, unbalanced(bad).size());
    }

    @Test void aBracketInsideQuotedTextIsNotCounted() {
        String ok = "@Query(\"SELECT r FROM X r WHERE r.note = ')' AND (r.a = 1)\")\n    List<X> find(int a);";
        assertEquals(List.of(), unbalanced(ok));
    }
}
