/**
 * Shapes mirror `ApiResponse<T>` and Spring Data `Page<T>` as used by every
 * Agriculture controller (see docs/agriculture/10-API-MAP.md).
 *
 * ASSUMPTION (documented as API GAP 9): the exact `ApiResponse` field names
 * were not directly inspected — only that controllers call
 * `ApiResponse.success(data)`, `ApiResponse.success(message, data)`, and
 * `ApiResponse.error(message)`. This is the most likely shape for a Spring
 * Boot project of this kind. Confirm against a real response or the
 * OpenAPI spec before removing this comment.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  message: string | null;
  data: T;
}

/** Mirrors Spring Data's default `Page<T>` JSON serialization. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index, 0-based
  size: number;
  first: boolean;
  last: boolean;
}

export interface ApiErrorPayload {
  success: false;
  message: string;
}
