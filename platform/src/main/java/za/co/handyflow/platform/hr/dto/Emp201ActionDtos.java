package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Emp201ActionDtos {
    private Emp201ActionDtos() {}

    /** @param paymentRef the payment reference SARS gave when the declaration was filed */
    public record SubmitRequest(String paymentRef) {}

    public record PayRequest(LocalDate paidOn, BigDecimal amount) {}
}
