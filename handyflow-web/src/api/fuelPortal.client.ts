// Dedicated axios client for the Fuel customer portal. The staff apiClient only attaches the staff token,
// so portal pages use the portal token held in usePortalAuthStore. Responses are unwrapped from ApiResponse.
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const fuelPortalApiClient = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

fuelPortalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

fuelPortalApiClient.interceptors.response.use(
  (response) => (response.data && typeof response.data === 'object' && 'data' in response.data ? { ...response, data: response.data.data } : response),
  (error) => {
    const status = error.response?.status
    const url = error.config?.url as string | undefined
    const isAuthEndpoint = url?.includes('/fuel/portal/auth/')
    // A 404 ("Not found") is how the server answers for an account the user has no access to, so it must not log them out.
    if ((status === 401 || status === 403) && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/fuel/portal/login'
    }
    return Promise.reject(error)
  }
)
