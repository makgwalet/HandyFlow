package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The escalation engine only follows requests up. These read its source (and the scheduler's and controller's) and fail if any of it could
 * decide, withdraw or use up an approval, or ask for a new one.
 */
class ApprovalEscalationNeverDecidesTest {

    private static final String BASE = "src/main/java/za/co/handyflow/platform/contracting/";
    private static final List<String> FILES = List.of(
            BASE + "application/internal/ApprovalEscalationService.java",
            BASE + "scheduler/ApprovalEscalationScheduler.java",
            BASE + "api/ContractApprovalEscalationController.java",
            BASE + "domain/model/EscalationPlanner.java",
            BASE + "domain/model/EscalationRules.java");

    /** Calls that change what an approval decided or whether it can be used. */
    private static final List<String> FORBIDDEN = List.of(
            "approvals.approve(", "approvals.reject(", "approvals.cancel(", "approvals.consume(", "approvals.request(", "approvals.assertApproved(",
            ".approve(", ".reject(", ".consume(", ".markStale(", ".invalidate(", ".setPolicy(", "setStatus(", "\"APPROVED\"", "\"REJECTED\"", "\"CONSUMED\"");

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    @Test
    void sourceNeverApprovesRejectsCancelsOrConsumes() throws IOException {
        for (String file : FILES) {
            String code = stripComments(read(file));
            for (String bad : FORBIDDEN) assertFalse(code.contains(bad), file + " must not contain " + bad);
        }
    }

    @Test
    void theOnlyApprovalServiceCallIsTheStaleCheckRead() throws IOException {
        String code = stripComments(read(FILES.get(0)));
        int calls = 0;
        for (int i = code.indexOf("approvals."); i >= 0; i = code.indexOf("approvals.", i + 1)) {
            calls++;
            assertTrue(code.startsWith("approvals.get(", i), "only approvals.get(...) may be called, found: " + code.substring(i, Math.min(code.length(), i + 30)));
        }
        assertTrue(calls >= 1);
    }

    @Test
    void theOnlyStatusChangeMadeToAnApprovalIsTheEscalationStageAndAssignment() throws IOException {
        String code = stripComments(read(FILES.get(0)));
        var m = java.util.regex.Pattern.compile("(?<![\\w.])a\\.(\\w+)").matcher(code);
        int seen = 0;
        while (m.find()) {
            seen++;
            String name = m.group(1);
            boolean ok = name.equals("assign") || name.equals("reachStage") || name.equals("escalationStage") || name.equals("approverAuthorities")
                    || name.startsWith("get") || name.startsWith("is");
            assertTrue(ok, "approval may only be read, assigned or staged, found: a." + name);
        }
        assertTrue(seen > 0);
    }

    @Test
    void theApprovalRepositoryIsOnlyReadOrSavedBack() throws IOException {
        String code = stripComments(read(FILES.get(0)));
        for (int i = code.indexOf("approvalRepo."); i >= 0; i = code.indexOf("approvalRepo.", i + 1)) {
            String rest = code.substring(i + "approvalRepo.".length());
            assertTrue(rest.startsWith("findForUpdate(") || rest.startsWith("findPendingIds(") || rest.startsWith("findRequestedBetween(") || rest.startsWith("save("),
                    "unexpected approval repository call: " + rest.substring(0, Math.min(rest.length(), 25)));
        }
    }

    private static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
