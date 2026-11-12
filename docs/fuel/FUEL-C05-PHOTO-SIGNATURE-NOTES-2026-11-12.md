# Fuel C05 leftover: offline photo and signature capture (patch 0792)

## Implemented
- Drivers can take a photo (camera or gallery) and capture a finger signature on a load that is on the road. Both go to the delivery's proof-of-delivery evidence.
- Photos are shrunk on the phone (longest side 1280 px, JPEG) before they are kept or sent, so several fit in the offline queue.
- With no signal the file is kept in the offline queue with its own request id and sent as an upload when the phone is online again. A repeat of the same request returns the first record and stores no second file.
- If the phone has no room to keep the item, the driver is told plainly; it is never shown as saved.
- Office staff can upload a photo, signature or document on a delivery and view stored files (fetched with their sign-in, opened from memory).
- The server decides the file type from the file's bytes (JPEG, PNG, WebP; PDF for documents only), limit 5 MB, and cleans the file name. If the record cannot be saved the stored file is removed.

## Preserved
Evidence stays append-only: replace-before-verify, verify by a different person, then locked. Typed-reference and GPS evidence are unchanged. The existing JSON evidence endpoints are unchanged.

## Files
- DB: V675 adds file_stored, content_type, file_size_bytes, file_name to fuel_delivery_evidence, with a check that a stored file has a key, type and size. Existing rows are unaffected.
- Java: FuelEvidenceFileService (new), EvidenceFileDtos, FuelDeliveryEvidence.createFile, FuelDeliveryEvidenceRepository.findOne, EvidenceView (3 new fields; a stored file shows its name, never the store's key), four endpoints on FuelDistributionController.
- Web: evidenceCapture.ts, SignaturePad.tsx, offlineQueue.ts (file on item, storage-full result), DriverTab, EvidenceModal, distributionOps.

## API
POST /api/v1/fuel/driver/loads/{id}/evidence/file and POST /api/v1/fuel/deliveries/{id}/evidence/file (multipart: file, kind, note, clientRequestId); GET /api/v1/fuel/evidence/{id}/file and GET /api/v1/fuel/driver/evidence/{id}/file. Same permissions as the existing evidence endpoints (read: FUEL_READ), fuel module guard, division routing unchanged. Drivers reach only their own loads. Downloads are nosniff, private, no-store.

## Tests
- Java: FuelEvidenceFileServiceTest (9) parse-checked only; not run.
- Web: evidenceCapture, SignaturePad, offlineQueue, DriverTab and EvidenceModal additions. Fuel and navigation suite 352 pass; tsc clean.
- V675 verified on a scratch PostgreSQL 16 (stored file without type or size is rejected).

## Gaps and risks
- Storage uses the shared service, whose default keeps files in the database. Fine for now; move to an object store before real volume.
- Replacing a wrong file still means withdrawing it and uploading a new one; there is no one-step replace with a file.
- No virus scan and no thumbnails; files open in a new tab.
- Camera capture relies on the phone browser's file input; the signature pad needs pointer events.
- Not run on a real phone or against the real file store; the multipart size limit is the app's 10 MB, under which the 5 MB rule applies.

## Next
Supplier banking details and requisitions (0793).
