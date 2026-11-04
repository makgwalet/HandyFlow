// src/api/fmPortal.client.ts
// Axios client for the Facilities Management client portal. Same shape as the other portals' clients: the portal token goes on every
// request, and a 401/403 ends the session unless it is a login attempt.
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const fmPortalApiClient = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

fmPortalApiClient.interceptors.request.use(config => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

fmPortalApiClient.interceptors.response.use(
  response => (response.data && 'data' in response.data ? { ...response, data: response.data.data } : response),
  error => {
    const status = error.response?.status
    const url = error.config?.url as string | undefined
    const isAuthEndpoint = url?.includes('/facilitiesmanagement/portal/auth/login') || url?.includes('/facilitiesmanagement/portal/auth/register')
    if ((status === 401 || status === 403) && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/facilitiesmanagement/portal/login'
    }
    return Promise.reject(error)
  },
)
