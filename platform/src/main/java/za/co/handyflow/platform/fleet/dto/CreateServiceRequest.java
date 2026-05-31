package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate;
public record CreateServiceRequest(
        @NotBlank String type, @NotBlank String description,
        @NotNull LocalDate serviceDate, Integer odometerAtService,
        Integer nextServiceKm, BigDecimal cost,
        String supplier, String invoiceRef
) {}