// src/api/client.ts
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export const api = axios.create({
  baseURL: API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // httpOnly refresh cookie
})

// Access token lives in memory (never localStorage)
let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

// Request interceptor: attach Bearer token
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken && config.headers) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// Response interceptor: queue requests while refreshing, then retry
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach((cb) => cb(newToken))
  refreshSubscribers = []
}

api.interceptors.response.use(
  (res) => res,
  async (err: AxiosError) => {
    const original = err.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (err.response?.status === 401 && original && !original._retry) {
      if (!isRefreshing) {
        isRefreshing = true
        original._retry = true

        try {
          const res = await axios.post(`${API_URL}/api/v1/auth/refresh`, {}, { withCredentials: true })
          const newToken = res.data.data.accessToken
          setAccessToken(newToken)
          onTokenRefreshed(newToken)
          isRefreshing = false
          return api(original)
        } catch (refreshErr) {
          isRefreshing = false
          refreshSubscribers = []
          // Force logout
          window.dispatchEvent(new CustomEvent('auth:session-expired'))
          return Promise.reject(refreshErr)
        }
      }

      // Queue other 401 requests while refresh is in flight
      return new Promise((resolve) => {
        subscribeTokenRefresh((newToken) => {
          original.headers.Authorization = `Bearer ${newToken}`
          resolve(api(original))
        })
      })
    }

    return Promise.reject(err)
  }
)