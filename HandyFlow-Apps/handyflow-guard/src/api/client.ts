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
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
  get isUnauthorized(): boolean {
    return this.status === 401;
  }
  get isNetwork(): boolean {
    return this.status === 0;
  }
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;

let tokenProvider: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = () => undefined;

export function configureApi(opts: { token: TokenProvider; onUnauthorized: UnauthorizedHandler }): void {
  tokenProvider = opts.token;
  onUnauthorized = opts.onUnauthorized;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  /** Skip the bearer header and the sign-out-on-401 behaviour (login endpoints). */
  anonymous?: boolean;
  signal?: AbortSignal;
}

const REQUEST_TIMEOUT_MS = 20_000;

/**
 * Calls the backend and returns the envelope's `data`.
 * Network failure is status 0, so callers can decide to queue instead of failing.
 */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (!opts.anonymous) {
    const token = tokenProvider();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), REQUEST_TIMEOUT_MS);
  opts.signal?.addEventListener("abort", () => timeout.abort());

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: opts.method ?? "GET",
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
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
    throw new ApiError(
      envelope?.message ?? `Request failed (${response.status})`,
      response.status,
    );
  }
  return envelope?.data as T;
}
