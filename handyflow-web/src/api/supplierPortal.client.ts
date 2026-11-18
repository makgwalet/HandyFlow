// Dedicated axios client for the supplier portal (RFQ quotations). Uses the portal token, never the staff token.
// Responses are unwrapped from ApiResponse.
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const supplierPortalApiClient = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

supplierPortalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

supplierPortalApiClient.interceptors.response.use(
  (response) => (response.data && typeof response.data === 'object' && 'data' in response.data ? { ...response, data: response.data.data } : response),
  (error) => {
    const status = error.response?.status
    const url = error.config?.url as string | undefined
    const isAuthEndpoint = url?.includes('/supplier/portal/auth/')
    // 404 is how the server answers for an RFQ this supplier was not invited to: it must not log them out.
    if ((status === 401 || status === 403) && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/supplier-portal/login'
    }
    return Promise.reject(error)
  }
)
