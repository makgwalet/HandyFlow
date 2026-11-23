package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the services, queries and controller still use them. */
class AgDocumentWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void saleDetailsAreKeptOnlyForASaleOrTransferOut() throws IOException {
        String code = src("application/internal/AgMovementRecordService.java");
        assertTrue(code.contains("boolean leaves = \"SALE\".equals(type) || \"TRANSFER_OUT\".equals(type);"));
        for (String f : new String[] {"buyerName", "buyerIdNumber", "buyerAddress", "destination", "vehicleRegistration"})
            assertTrue(code.contains("leaves ? req." + f + "() : null"), f);
        assertTrue(code.contains("m.getBuyerName(), m.getBuyerIdNumber(), m.getBuyerAddress(), m.getDestination(), m.getVehicleRegistration()"));
    }

    @Test void theDocumentIsBuiltByTheRulesAndDrawnByThePdfClassOnly() throws IOException {
        String svc = src("application/internal/AgMovementDocumentService.java");
        assertTrue(svc.contains("AgSaleDocumentRules.requireDocumentable(m.getMovementType())"));
        assertTrue(svc.contains("AgSaleDocumentRules.build("));
        assertTrue(svc.contains("pdf.render(doc)"));
        assertTrue(svc.contains("farm.getOwnerName()") && svc.contains("farm.getBrandMark()") && svc.contains("m.getBuyerName()"));
        String pdf = src("application/internal/AgSaleDocumentPdf.java");
        assertTrue(pdf.contains("doc.warnings()") && pdf.contains("doc.totalHead()") && pdf.contains("doc.number()"));
        assertFalse(pdf.contains("if (\"SALE\""), "the PDF class decides nothing");
        assertEquals(2, pdf.split("doc\\.warnings\\(\\)", -1).length - 1, "warnings are tested for and printed");
    }

    @Test void everyDownloadIsGuardedAndNamedSafely() throws IOException {
        String c = src("api/AgDocumentController.java");
        assertEquals(4, c.split("@PreAuthorize\\(\"hasAuthority\\('AGRICULTURE_READ'\\)\"\\)", -1).length - 1);
        assertEquals(4, c.split("featureGuard.requireModule\\(\"agriculture\"\\)", -1).length - 1);
        assertEquals(2, c.split("ContentDisposition.attachment\\(\\).filename\\(", -1).length - 1, "no raw header strings");
        assertFalse(c.contains("\"attachment; filename="));
        String r = src("application/internal/AgRegisterService.java");
        assertEquals(3, r.split("AgCsv.fileName\\(", -1).length - 1);
        assertTrue(r.contains("MAX_ROWS = 20_000"));
        assertEquals(7, r.split("PageRequest.of\\(0, MAX_ROWS\\)", -1).length - 1, "every register read is capped");
    }

    @Test void registersAreScopedToTheTenantAndTheFarm() throws IOException {
        String h = src("domain/repository/AgHealthEventRepository.java");
        String register = h.substring(h.indexOf("findForFarmBetween") - 700, h.indexOf("findForFarmBetween"));
        assertTrue(register.contains("a.tenantId = :tenantId AND a.farmId = :farmId") && register.contains("g.tenantId = :tenantId AND g.farmId = :farmId"));
        String i = src("domain/repository/AgInputApplicationRepository.java");
        assertTrue(i.contains("c.tenantId = :tenantId AND c.farmId = :farmId"));
        assertTrue(i.contains("a.applicationDate >= :from AND a.applicationDate <= :to"));
    }

    @Test void ownerDetailsAreOnlyChangedWhenSent() throws IOException {
        String f = src("application/internal/AgFarmService.java");
        assertTrue(f.contains("req.ownerName() != null ? req.ownerName() : farm.getOwnerName()"));
        assertTrue(f.contains("req.physicalAddress() != null ? req.physicalAddress() : farm.getPhysicalAddress()"));
        assertTrue(f.contains("f.getOwnerName(), f.getPhysicalAddress()"));
    }
}
