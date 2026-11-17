import { API_BASE_URL } from "@/config/env";

/** Envelope every HandyFlow endpoint returns. */
export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data?: T;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
  /** Sign-in expired or was revoked. */
  get isUnauthorized(): boolean { return this.status === 401; }
  /** The company's Fuel subscription is not active. Nothing the driver does can fix it. */
  get isPaymentRequired(): boolean { return this.status === 402; }
  /** This user may not do this. */
  get isForbidden(): boolean { return this.status === 403; }
  /** No signal, DNS failure or timeout. */
  get isNetwork(): boolean { return this.status === 0; }
}

type TokenProvider = () => string | null;

let tokenProvider: TokenProvider = () => null;
let onUnauthorized: () => void = () => undefined;

export function configureApi(opts: { token: TokenProvider; onUnauthorized: () => void }): void {
  tokenProvider = opts.token;
  onUnauthorized = opts.onUnauthorized;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  /** A multipart upload (photos). Sent as is; the platform sets the content type and boundary. */
  form?: FormData;
  /** Skip the bearer header and the sign-out-on-401 behaviour (login). */
  anonymous?: boolean;
  signal?: AbortSignal;
}

const REQUEST_TIMEOUT_MS = 20_000;
const UPLOAD_TIMEOUT_MS = 60_000;

/** Calls the backend and returns the envelope's `data`. Network failure is status 0 so callers can queue instead of failing. */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (!opts.anonymous) {
    const token = tokenProvider();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), opts.form ? UPLOAD_TIMEOUT_MS : REQUEST_TIMEOUT_MS);
  opts.signal?.addEventListener("abort", () => timeout.abort());

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: opts.method ?? "GET",
      headers,
      body: opts.form ?? (opts.body === undefined ? undefined : JSON.stringify(opts.body)),
      signal: timeout.signal,
    });
  } catch {
    throw new ApiError("Cannot reach the server. Check your signal and try again.", 0);
  } finally {
    clearTimeout(timer);
  }

  let envelope: ApiEnvelope<T> | undefined;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch {
    envelope = undefined;
  }

  if (!response.ok || envelope?.success === false) {
    if (response.status === 401 && !opts.anonymous) onUnauthorized();
    throw new ApiError(envelope?.message ?? `Request failed (${response.status})`, response.status);
  }
  return envelope?.data as T;
}
