// src/api/auditorPortal.client.ts
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// Mirrors the proven per-module portal client pattern (payrollBureauPortal.client.ts,
// recruitmentAgencyPortal.client.ts, bookingAgencyPortal.client.ts) — NOT the
// generic portalClient.ts, which was explicitly retracted earlier this
// session in favor of exactly this per-module shape.
export const auditorPortalApiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

auditorPortalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

auditorPortalApiClient.interceptors.response.use(
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

    const isAuthEndpoint = url?.includes('/auditor/portal/auth/login')
      || url?.includes('/auditor/portal/auth/register')

    // Confirmed verbatim — this is the exact string this session's own
    // AuditorPortalDataService.requireActiveGrant() throws, not inferred
    // from another module's convention.
    const isPermissionDenial = message === "You don't have access to this business's records"

    if ((status === 401 || status === 403) && !isPermissionDenial && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/auditor/portal/login'
    }
    return Promise.reject(error)
  }
)
