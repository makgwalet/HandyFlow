// src/api/portal.client.ts
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

/**
 * Deliberately a SEPARATE axios instance from the staff apiClient
 * (client.ts) — not just a different token, a genuinely different
 * client, so there's no code path by which a portal request could
 * accidentally carry a staff token or vice versa. Matches the
 * backend's own separate PortalJwtFilter/PortalJwtService design.
 *
 * No withCredentials/cookie handling and no refresh-token queue —
 * client.ts's sophistication there exists specifically for the staff
 * refresh-token rotation mechanism, and the portal backend has no
 * refresh endpoint at all. PortalJwtService issues a single, long-lived
 * (7-day) token deliberately, so portal users don't need frequent
 * re-login — there's nothing to refresh TO, so none of that complexity
 * applies here.
 */
export const portalApiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

portalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Unwrap the ApiResponse wrapper, matching client.ts's own convention.
portalApiClient.interceptors.response.use(
  (response) => {
    if (response.data && 'data' in response.data) {
      return { ...response, data: response.data.data }
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message as string | undefined
    const url = error.config?.url as string | undefined

    // FIX: was redirecting to '/accountant-portal/login' — a path that
    // was never actually wired into App.tsx (the real route is
    // '/accountant/portal/login', with no hyphen). Confirmed via real
    // testing: a wrong password on login was silently redirecting to
    // the staff SaaS app via the router's catch-all, instead of
    // showing "Invalid email or password" on the login page. Stale
    // path from before the final namespace was locked in, never
    // propagated back to this file.
    //
    // FIX (deeper issue, not just the typo): a failed login or
    // register attempt should never trigger this "session died, log
    // out and redirect" handling at all — there is no session yet to
    // log out of, and the user is already sitting on the page this
    // would redirect them back to. Mirrors how the staff client.ts
    // exempts its own /auth/refresh endpoint from this same class of
    // handling, for the same reason: a failure at the entry point
    // itself is a normal error for the calling page to display inline,
    // not a session-death signal.
    const isAuthEndpoint = url?.includes('/accountant/portal/auth/login')
      || url?.includes('/accountant/portal/auth/register')

    // WHY check message text, not just status code?
    //
    // Confirmed against this backend's real filter structure:
    // PortalJwtFilter mirrors JwtAuthFilter exactly, which — per
    // client.ts's own confirmed finding — never actually throws 401
    // for an invalid/expired/missing token. It silently sets no
    // authentication, and Spring Security's .anyRequest().authenticated()
    // rejects the request, which resolves as 403. That means "your
    // session is dead" and "your session is fine but you don't have
    // access to THIS specific client" (a real, legitimate 403 from
    // AccPortalAccessGrantRepository's own access check) are both 403
    // here — genuinely ambiguous by status code alone.
    //
    // A real response from this backend carries no separate structured
    // error-code field, only a message string — so message text is the
    // only signal available to tell these two cases apart. Blindly
    // logging out on every 403 would incorrectly kill a perfectly valid
    // session just because the user clicked into (or was linked to) a
    // client they don't have access to.
    const isPermissionDenial = message === "You don't have access to this client"

    if ((status === 401 || status === 403) && !isPermissionDenial && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/accountant/portal/login'
    }
    return Promise.reject(error)
  }
)