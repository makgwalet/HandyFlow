package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the queries, services and controllers still use them. */
class AgListWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void queriesUseTheSameEscapeCharacterAsTheRules() throws IOException {
        String rules = src("domain/rules/AgListRules.java");
        assertTrue(rules.contains("public static final char ESCAPE = '!';"));
        for (String repo : new String[] {"domain/repository/AgAnimalRepository.java", "domain/repository/AgGroupRepository.java"}) {
            String all = src(repo);
            int at = all.indexOf("> searchForFarm(");
            assertTrue(at > 0, repo);
            String code = all.substring(all.lastIndexOf("@Query(", at), at);   // just this query
            assertTrue(code.contains("(:status = '' OR"), repo + " status is never bound as null");
            assertTrue(code.contains("LIKE :pattern ESCAPE '!'"), repo);
            assertTrue(code.contains(".deletedAt IS NULL"), repo + " leaves out deleted rows");
            assertTrue(code.contains("tenantId = :tenantId AND") && code.contains("farmId = :farmId"), repo + " is scoped to tenant and farm");
        }
        assertEquals(3, src("domain/repository/AgAnimalRepository.java").split("LIKE :pattern ESCAPE '!'", -1).length - 1, "tag, name, breed");
        assertEquals(2, src("domain/repository/AgGroupRepository.java").split("LIKE :pattern ESCAPE '!'", -1).length - 1, "batch, breed");
    }

    @Test void servicesClampThePageAndUseTheRules() throws IOException {
        for (String f : new String[] {"application/internal/AgAnimalService.java", "application/internal/AgGroupService.java"}) {
            String code = src(f);
            assertTrue(code.contains("AgListRules.pageSize(pageable.getPageSize())"), f);
            assertTrue(code.contains("AgListRules.pageIndex(pageable.getPageNumber())"), f);
            assertTrue(code.contains("AgListRules.status(status), AgListRules.likePattern(q), page)"), f);
            assertFalse(code.contains("findByStatusForFarm(tenantId, farmId, status, pageable)"), f + " no longer lists with the caller's own page");
        }
    }

    @Test void controllersPassTheSearchText() throws IOException {
        for (String f : new String[] {"api/AgAnimalController.java", "api/AgGroupController.java"}) {
            String code = src(f);
            assertTrue(code.contains("@RequestParam(required = false) String q,"), f);
            assertTrue(code.contains("farmId, status, q, pageable)"), f);
            assertTrue(code.contains("@PageableDefault(size = 50) Pageable pageable"), f);
        }
    }
}
