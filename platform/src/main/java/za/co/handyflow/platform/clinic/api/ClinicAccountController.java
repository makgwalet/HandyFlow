package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicAccountService;
import za.co.handyflow.platform.clinic.dto.AccountDtos.AccountResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Clinic patient account", description = "Charges, claims, payments and balance per patient")
public class ClinicAccountController {

    private final ClinicAccountService service;

    @GetMapping("/account")
    @PreAuthorize("hasAuthority('CLINIC_BILL_READ')")
    @Operation(summary = "The patient account: each visit with its claim status, payments received and the balance owing")
    public ResponseEntity<ApiResponse<AccountResponse>> account(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.account(TenantContext.getTenantIdAsObject(), patientId)));
    }
}
