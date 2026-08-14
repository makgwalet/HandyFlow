// src/api/bookingAgencyPortal.client.ts
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const bookingAgencyPortalApiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

bookingAgencyPortalApiClient.interceptors.request.use((config) => {
  const token = usePortalAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

bookingAgencyPortalApiClient.interceptors.response.use(
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

    const isAuthEndpoint = url?.includes('/booking-agency/portal/auth/login')
      || url?.includes('/booking-agency/portal/auth/register')

    // UNVERIFIED: BookingAgencyPortalDataService's own header comment
    // calls itself "the third mirror of the same confirmed-working
    // pattern" as accountant/recruitment-agency, which strongly implies
    // its requireAccess() throws the identical message — but I have not
    // directly read that method's source the way I confirmed the other
    // two. If permission-denial 403s are incorrectly logging someone
    // out here, this exact string is the first thing to check against
    // the real BookingAgencyPortalDataService.requireAccess().
    const isPermissionDenial = message === "You don't have access to this client"

    if ((status === 401 || status === 403) && !isPermissionDenial && !isAuthEndpoint) {
      usePortalAuthStore.getState().logout()
      window.location.href = '/booking-agency/portal/login'
    }
    return Promise.reject(error)
  }
)