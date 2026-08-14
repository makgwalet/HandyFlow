// src/api/payrollBureauPortal.client.ts
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const payrollBureauPortalApiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

payrollBureauPortalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

payrollBureauPortalApiClient.interceptors.response.use(
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

    const isAuthEndpoint = url?.includes('/payroll-bureau/portal/auth/login')
      || url?.includes('/payroll-bureau/portal/auth/register')

    // Confirmed verbatim against PayrollBureauPortalDataService.
    // requireAccess() — same message accountant's own check matches.
    const isPermissionDenial = message === "You don't have access to this client"

    if ((status === 401 || status === 403) && !isPermissionDenial && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/payroll-bureau/portal/login'
    }
    return Promise.reject(error)
  }
)