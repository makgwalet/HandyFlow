// fuel/dto/CompleteDeliveryRequest.java

package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteDeliveryRequest(
        @NotNull BigDecimal litresDelivered,
        String receiverName,        // person at mine who accepted delivery
        String receiverIdBadge,     // their ID or site badge number
        BigDecimal meterReadingStart, // pump meter at start
        BigDecimal meterReadingEnd,    // pump meter at end — confirms litres
        // WHY? Captures when designated receiver is absent
        // Driver records who actually signed and on whose behalf
        Boolean signedOnBehalf,
        String onBehalfOf          // name of the designated receiver being represented
) {}