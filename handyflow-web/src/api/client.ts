// src/api/client.ts

import axios from 'axios'
import { useAuthStore } from '../store/auth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  // NEW: required for the httpOnly refresh_token cookie to be sent and
  // received at all. localhost:5173 -> localhost:8080 is cross-origin
  // (different ports), and axios does not attach cookies cross-origin
  // by default no matter what the server's CORS config allows — this
  // flag is the frontend half of that agreement.
  withCredentials: true,
})

// WHY request interceptor?
// Automatically inject JWT token into every request.
// No need to pass token manually in every API call.
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Refresh-on-401-or-403, with request queuing ─────────────────────────────
//
// WHY 403 too, not just 401?
//
// Confirmed via real testing: this app's JwtAuthFilter has no else branch
// when a token fails validation — it silently proceeds with no
// authentication set rather than throwing, and it's @PreAuthorize on each
// endpoint that ultimately rejects the request, which resolves as 403.
// An expired or invalid access token — the single most common case this
// whole mechanism exists for — surfaces here as 403, not 401. See the
// retry-still-403 handling further down for how genuine permission
// denials (a properly-authenticated user just lacking one specific
// authority) are kept from being misread as an auth failure.
//
// WHY a queue instead of just calling /refresh directly inside the
// handler?
//
// Refresh tokens rotate on the backend — the old one is revoked the
// instant a new one is issued (see RefreshTokenService.refresh()). If a
// page fires several API calls in parallel and the access token happens
// to have just expired, all of them fail at roughly the same moment. If
// each one independently triggered its own /refresh call, only the FIRST
// would succeed — every other one would be presenting a token that's
// already been rotated away by the first, which the backend correctly
// treats as reuse. That's not a hypothetical: reuse detection responds by
// revoking every active session for that user (confirmed working via
// real testing). A purely client-side race condition would look, to the
// backend, identical to a stolen token being used from two places at
// once, and would log the user out of everywhere as a result — the exact
// failure mode this queue exists to prevent.
//
// Only one refresh is ever in flight. Every other failed request queues
// up and waits for that single refresh to resolve, then retries with
// whatever token it produces.

let isRefreshing = false
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

function flushQueue(error: unknown, token: string | null) {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token)
    else reject(error)
  })
  pendingQueue = []
}

function forceLogout() {
  useAuthStore.getState().logout()
  window.location.href = '/login'
}

// WHY response interceptor?
// Unwrap the ApiResponse wrapper automatically.
// Every API call gets the data directly, not {success, message, data}
apiClient.interceptors.response.use(
  (response) => {
    // Unwrap our ApiResponse wrapper
    if (response.data && 'data' in response.data) {
      return { ...response, data: response.data.data }
    }
    return response
  },
  async (error) => {
    const originalRequest = error.config
    const status = error.response?.status

    // FIX: was only checking for 401. Confirmed via real testing this app
    // never actually produces 401 for an invalid/expired access token —
    // JwtAuthFilter has no else branch when isTokenValid() returns false;
    // it silently proceeds with no authentication set at all rather than
    // throwing, and it's @PreAuthorize on each endpoint that ultimately
    // rejects the unauthenticated request, which Spring Security resolves
    // as AccessDeniedException -> 403. The single most common case this
    // entire mechanism exists for — an expired access token — was never
    // triggering the refresh flow at all before this fix.
    //
    // 403 is genuinely ambiguous in this app though: it also means a
    // properly-authenticated user just lacks a specific permission, and
    // that case must never be treated the same as an auth failure — see
    // the retry-still-403 handling below for how that's kept safe.
    if ((status !== 401 && status !== 403) || !originalRequest) {
      return Promise.reject(error)
    }

    // The refresh call itself failing means there's no valid session
    // left to recover from — don't try to refresh a refresh, just log
    // out. Without this check, a genuinely expired/invalid refresh token
    // would loop: refresh fails -> interceptor tries to refresh the
    // failed refresh -> fails again -> forever.
    if (originalRequest.url?.includes('/api/v1/auth/refresh')) {
      forceLogout()
      return Promise.reject(error)
    }

    // Already retried this specific request once. If it's STILL 401,
    // the refreshed token was also rejected — a genuine session problem,
    // log out. If it's 403 on the retry specifically, the refresh above
    // already succeeded (that's the only way execution reaches a second
    // pass with _retry set) — meaning the session itself is fine, and
    // this is a real permission denial, not an auth failure. Let it
    // propagate as a normal error instead of logging the user out just
    // because they hit a screen they don't have access to.
    if (originalRequest._retry) {
      if (status === 401) {
        forceLogout()
      }
      return Promise.reject(error)
    }
    originalRequest._retry = true

    if (isRefreshing) {
      // A refresh is already in flight for some other request that hit
      // 401/403 first — queue this one instead of starting a second,
      // competing refresh that would rotate the token out from under it.
      return new Promise((resolve, reject) => {
        pendingQueue.push({
          resolve: (newToken: string) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(apiClient(originalRequest))
          },
          reject,
        })
      })
    }

    isRefreshing = true

    try {
      // Cookie is sent automatically (withCredentials above) — no body
      // needed, the backend reads the refresh token from the cookie, not
      // the request.
      const refreshResponse = await apiClient.post('/api/v1/auth/refresh')
      // Already unwrapped by this same interceptor's success branch by
      // the time it reaches here.
      const data = refreshResponse.data as {
        accessToken: string; userId: string; tenantId: string; email: string
        firstName: string; lastName: string; permissions: string[]
      }

      // Preserve whatever else might already be on the stored user
      // object rather than reconstructing one from scratch — this file
      // only knows the fields the refresh response actually returns, not
      // the full shape of the User type.
      const currentUser = useAuthStore.getState().user
      useAuthStore.getState().setAuth(data.accessToken, {
        ...(currentUser ?? {}),
        userId: data.userId,
        tenantId: data.tenantId,
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        permissions: data.permissions,
      } as any)

      flushQueue(null, data.accessToken)
      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
      return apiClient(originalRequest)
    } catch (refreshError) {
      flushQueue(refreshError, null)
      forceLogout()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)