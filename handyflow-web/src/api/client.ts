// src/api/client.ts

import axios from 'axios'
import { useAuthStore } from '../store/auth.store'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

// WHY request interceptor?
// Automatically inject JWT token into every request.
// No need to pass token manually in every API call.
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// WHY response interceptor?
// Unwrap the ApiResponse wrapper automatically.
// Every API call gets the data directly, not {success, message, data}
apiClient.interceptors.response.use(
  (response) => {
    // Unwrap our ApiResponse wrapper
    if (response.data && 'data' in response.data) {
      return { ...response, data: response.data.data }
    }
    return response
  },
  (error) => {
    // Handle 401 globally — redirect to login
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)