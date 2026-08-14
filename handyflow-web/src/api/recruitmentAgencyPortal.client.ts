// src/api/recruitmentAgencyPortal.client.ts
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

/**
 * Mirrors portal.client.ts (Accountant's own portal client) exactly —
 * that file already fixed this class of bug once (hardcoded wrong
 * redirect path, and treating every 401/403 as session-death instead
 * of distinguishing a real permission denial). Not re-derived from
 * scratch — copied deliberately.
 */
export const recruitmentAgencyPortalApiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

recruitmentAgencyPortalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

recruitmentAgencyPortalApiClient.interceptors.response.use(
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

    const isAuthEndpoint = url?.includes('/recruitment-agency/portal/auth/login')
      || url?.includes('/recruitment-agency/portal/auth/register')

    // Confirmed verbatim against RecruitmentAgencyPortalDataService.
    // requireAccess() — same message accountant's own check matches
    // against, same reasoning: a 403 from clicking a client you don't
    // have a grant for is not the same thing as a dead session.
    const isPermissionDenial = message === "You don't have access to this client"

    if ((status === 401 || status === 403) && !isPermissionDenial && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/recruitment-agency/portal/login'
    }
    return Promise.reject(error)
  }
)