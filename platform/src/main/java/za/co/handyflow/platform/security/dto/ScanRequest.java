package za.co.handyflow.platform.security.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.util.UUID;
// Add scanType to support future hardware tags
public record ScanRequest(
        String   qrCode,      // null if NFC/BLE scan
        String   nfcTagId,    // null if QR scan
        String   bleBeaconId, // null if not BLE
        UUID     guardId,
        UUID     shiftId,
        BigDecimal   latitude,
        BigDecimal   longitude,
        Double   accuracy,    // GPS accuracy in metres
        String   scanType,    // QR | NFC | BLE | GPS_PING | MANUAL
        String   deviceId     // phone IMEI or device identifier
) {}