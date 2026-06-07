// src/api/client.ts
import axios from 'axios'

export const adminApi = axios.create({ baseURL: '/api/v1/admin' })

adminApi.interceptors.request.use(cfg => {
  const token = localStorage.getItem('hf_admin_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

adminApi.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('hf_admin_token')
      localStorage.removeItem('hf_admin_user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// Separate instance for auth endpoints — no token attached
export const authApi = axios.create({ baseURL: '/api/v1/admin/auth' })
