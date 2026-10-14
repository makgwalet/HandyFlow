package za.co.handyflow.platform.capture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.capture.application.CaptureService;
import za.co.handyflow.platform.capture.dto.CaptureResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.BusinessException;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/capture")
@Tag(name = "Document capture")
public class CaptureController {

    private final CaptureService service;

    public CaptureController(CaptureService service) {
        this.service = service;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('USER_CREATE','EXPENSES_MANAGE','AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Read a receipt or supplier invoice PDF and suggest fields. Nothing is stored or created.")
    public ResponseEntity<ApiResponse<CaptureResponse>> extract(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Success", service.extract(file.getBytes())));
        } catch (IOException e) {
            throw new BusinessException("The file could not be read.");
        }
    }
}
