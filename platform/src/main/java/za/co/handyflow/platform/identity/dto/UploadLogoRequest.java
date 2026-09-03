package za.co.handyflow.platform.identity.dto;

import jakarta.validation.constraints.Size;

// FIX (identity module modernization), two separate bugs in this one
// small file:
//
// 1) logoBase64 had no size bound at all beyond the @NotBlank removed
//    below — SettingsPage.tsx enforces a 200KB *file* size before it
//    ever base64-encodes and uploads, but that's a client-side
//    convenience check only, not a security boundary (this codebase's
//    own stated principle — see CLAUDE.md's "Frontend visibility is not
//    a security boundary" rule). A direct API call could send an
//    arbitrarily large payload straight into Tenant.logoUrl, which is
//    stored as raw text with no DB-level size limit either. The bound
//    here is deliberately generous relative to the frontend's 200KB
//    file-size check — base64 inflates raw bytes by ~4/3, and a caller
//    may or may not include the "data:image/...;base64," prefix
//    TenantService.uploadLogo() itself tolerates either way — so this
//    caps the encoded STRING at roughly 1MB of decoded image data,
//    comfortably above the frontend's real ceiling, while still ruling
//    out multi-megabyte abuse payloads.
//
// 2) @NotBlank was removed entirely, not just loosened: SettingsPage.tsx
//    "Remove logo" button calls this exact endpoint with
//    `logoBase64: ""` to clear the logo (confirmed directly in that
//    file) — @NotBlank rejected that with 400 on every attempt, so
//    removing a logo was completely broken. @Size treats null/blank as
//    valid by design (it only constrains the length of a value that IS
//    present), so this alone doesn't accept blank; TenantService.
//    uploadLogo() is what now gives blank an explicit, correct meaning
//    ("clear the logo") instead of silently wrapping it into a garbage
//    "data:image/png;base64," placeholder the way it used to.
public record UploadLogoRequest(
        @Size(max = 1_400_000, message = "Logo file is too large — please use an image under 1MB")
        String logoBase64,   // data:image/png;base64,... or raw base64 — blank/null clears the logo
        String mimeType                // image/png, image/jpeg, image/svg+xml
) {}
