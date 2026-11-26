package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayEmp501Dtos {

    private PayEmp501Dtos() {}

    public record FindingResponse(String severity, String code, String message) {}
    public record LineResponse(String code, BigDecimal amount) {}

    public record CertificateResponse(UUID employeeId, String employeeName, String type, List<LineResponse> income,
                                      List<LineResponse> deductions, BigDecimal nonTaxable3696, BigDecimal grossTaxable3699,
                                      BigDecimal paye4102, BigDecimal uif4141, BigDecimal sdl4142, BigDecimal total4149,
                                      boolean blocked, List<FindingResponse> findings) {}

    public record DifferenceResponse(String what, BigDecimal emp201, BigDecimal certificates, BigDecimal difference) {}

    public record Emp501Response(UUID clientId, String period, int taxYear, LocalDate from, LocalDate to, int certificateCount,
                                 boolean balanced, boolean blocked, List<DifferenceResponse> differences,
                                 BigDecimal etiUsed, BigDecimal etiUnusedAtEnd, List<FindingResponse> findings,
                                 List<CertificateResponse> certificates) {}
}
