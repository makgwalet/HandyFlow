package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgAnimalService;
import za.co.handyflow.platform.agriculture.application.internal.AgAnimalTagService;
import za.co.handyflow.platform.agriculture.dto.AnimalResponse;
import za.co.handyflow.platform.agriculture.dto.ReplaceTagRequest;
import za.co.handyflow.platform.agriculture.dto.TagHistoryResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Replacing a newborn's temporary tag with its permanent one, and the tags an animal has had. */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Animal tags", description = "Temporary tags given at birth and their replacement")
public class AgAnimalTagController {

    private static final String SOURCE_MODULE = "agriculture";

    private final AgAnimalTagService tagService;
    private final AgAnimalService animalService;
    private final FeatureGuard featureGuard;

    @PatchMapping("/animals/{id}/tag")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Replace a temporary tag with the permanent tag number")
    public ResponseEntity<ApiResponse<AnimalResponse>> replaceTag(@PathVariable UUID id, @Valid @RequestBody ReplaceTagRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        tagService.replaceTemporaryTag(TenantContext.getTenantIdAsObject(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Tag replaced", animalService.getAnimal(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/animals/{id}/tag-history")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<List<TagHistoryResponse>>> history(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(tagService.history(TenantContext.getTenantIdAsObject(), id)));
    }
}
