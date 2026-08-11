// security/dto/SetQrEnforcementRequest.java
package za.co.handyflow.platform.security.dto;

public record SetQrEnforcementRequest(
        boolean requireSignedQr
) {}