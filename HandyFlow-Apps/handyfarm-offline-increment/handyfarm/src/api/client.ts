import axios, { AxiosError } from 'axios';
import * as SecureStore from 'expo-secure-store';
import { ApiEnvelope } from './types';
import { enqueueMutation } from '../offline/queue';

/**
 * ASSUMPTION (documented as API GAP 10): the exact auth/tenant mechanism
 * used by the rest of HandyFlow was not inspected — only that every
 * Agriculture endpoint resolves tenant server-side via
 * `TenantContext.getTenantIdAsObject()`. This client assumes a bearer
 * token is enough and there is nothing Agriculture-specific to add.
 * Replace AUTH_TOKEN_KEY / the header logic below once the real auth
 * module has been inspected.
 */
const AUTH_TOKEN_KEY = 'handyflow_auth_token';

// TODO: wire to real environment config (EAS build profiles / app.config.ts).
const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

apiClient.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync(AUTH_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export class ApiError extends Error {
  status?: number;
  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const message = error.response?.data?.message ?? error.message ?? 'Something went wrong.';
    return Promise.reject(new ApiError(message, error.response?.status));
  }
);

/** Unwraps `ApiEnvelope<T>` down to `T`. Every service function should go through this. */
export async function unwrap<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const response = await promise;
  return response.data.data;
}

/**
 * The result of a write that might have gone offline. `synced` means the
 * server confirmed it and `data` is real. `queued` means there was no
 * network — the request was saved to the local offline queue
 * (see src/offline/) instead of being lost, and will be replayed once
 * connectivity returns. Screens must branch on `status` rather than
 * assuming `data` is always present — see docs/agriculture/12-OFFLINE-ARCHITECTURE.md.
 */
export type WriteResult<T> = { status: 'synced'; data: T } | { status: 'queued' };

/**
 * Attempts a write (POST/PATCH/PUT). On a genuine network failure (no
 * response came back at all — offline, DNS failure, timeout), queues the
 * request instead of throwing, so the caller can tell the user their work
 * was saved on-device. On any server-returned error (validation, 4xx/5xx),
 * rethrows as a normal ApiError — those are real problems the person needs
 * to see and fix, not connectivity issues.
 */
export async function writeOrQueue<T>(
  config: { method: 'POST' | 'PATCH' | 'PUT'; url: string; data?: unknown },
  entityLabel: string
): Promise<WriteResult<T>> {
  try {
    const response = await apiClient.request<ApiEnvelope<T>>(config);
    return { status: 'synced', data: response.data.data };
  } catch (err) {
    // A genuine network failure surfaces here as an ApiError with no
    // status (see the response interceptor above — no error.response means
    // no status was set). Anything with a status is a real server error.
    if (err instanceof ApiError && err.status === undefined) {
      await enqueueMutation({ method: config.method, url: config.url, body: config.data, entityLabel });
      return { status: 'queued' };
    }
    throw err;
  }
}
