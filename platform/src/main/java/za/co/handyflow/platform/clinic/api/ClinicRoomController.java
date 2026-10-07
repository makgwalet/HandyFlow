package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicRoomService;
import za.co.handyflow.platform.clinic.dto.RoomDtos.CreateRoomRequest;
import za.co.handyflow.platform.clinic.dto.RoomDtos.RoomResponse;
import za.co.handyflow.platform.clinic.dto.RoomDtos.UpdateRoomRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/rooms")
@RequiredArgsConstructor
@Tag(name = "Clinic rooms", description = "Consulting rooms that appointments can be booked into")
public class ClinicRoomController {

    private final ClinicRoomService rooms;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Rooms, A to Z. Switched-off rooms are only included with includeInactive=true.")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success("Success", rooms.list(TenantContext.getTenantIdAsObject(), includeInactive)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_ADMIN')")
    @Operation(summary = "Add a room. A duplicate name (ignoring case) is refused with 409.")
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody CreateRoomRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Room added", rooms.create(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLINIC_ADMIN')")
    @Operation(summary = "Rename a room or switch it on or off. Past appointments keep the room.")
    public ResponseEntity<ApiResponse<RoomResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoomRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Room updated", rooms.update(TenantContext.getTenantIdAsObject(), id, req)));
    }
}
