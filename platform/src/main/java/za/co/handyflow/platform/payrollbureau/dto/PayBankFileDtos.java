package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PayBankFileDtos {

    private PayBankFileDtos() {}

    public record CreateBankFileRequest(String reason) {}

    public record BankFileResponse(UUID id, int fileNumber, String status, int paymentCount, BigDecimal totalAmount,
                                   List<String> excluded, String reason, Instant createdAt, long downloads) {}

    /** A generated file and the name to save it under. */
    public record GeneratedFile(String fileName, byte[] content) {}
}
