// security/dto/GuardLoginRequest.java
package za.co.handyflow.platform.security.dto;

/**
 * GuardLoginRequest — CHANGE (V214): added employeeCode as an alternative
 * identifier to phone. Exactly one of phone/employeeCode should be provided;
 * GuardAuthService.resolveGuardForLogin() checks phone first, falls back to
 * employeeCode, and rejects the request if neither is present.
 *
 * NOTE: I do not have this file's previously-existing content -- reconstructed
 * from GuardAuthService's usage (req.phone(), req.pin(), req.deviceId()).
 * Diff against your actual file before applying, same caveat as
 * GuardResponse/PrincipalResponse this session.
 */
public record GuardLoginRequest(
        String phone,
        String employeeCode,
        String pin,
        String deviceId
) {}