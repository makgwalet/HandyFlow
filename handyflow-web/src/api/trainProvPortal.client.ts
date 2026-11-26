// src/api/trainProvPortal.client.ts
// Axios client for the Training Provider learner and employer portals. The portal token (never the staff token) goes on every request, and a 401/403
// ends the session unless it is a sign-in attempt.
import axios from 'axios'
import { usePortalAuthStore } from '../store/portalAuth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const trainProvPortalClient = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

trainProvPortalClient.interceptors.request.use(config => {
  const token = usePortalAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

trainProvPortalClient.interceptors.response.use(
  response => (response.data && 'data' in response.data ? { ...response, data: response.data.data } : response),
  error => {
    const status = error.response?.status
    const url = error.config?.url as string | undefined
    const isAuthEndpoint = url?.includes('/training-provider/portal/auth/') || url?.includes('/training-provider/portal/learner/auth/')
    if ((status === 401 || status === 403) && !isAuthEndpoint) {
      const learner = url?.includes('/portal/learner/')
      usePortalAuthStore.getState().logout()
      window.location.href = learner ? '/training-provider/portal/learner/login' : '/training-provider/portal/login'
    }
    return Promise.reject(error)
  },
)
