// src/api/auth.api.ts

import { apiClient } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth.types'

export const authApi = {
  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const res = await apiClient.post('/api/v1/auth/register', data)
    return res.data
  },

  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const res = await apiClient.post('/api/v1/auth/login', data)
    return res.data
  },
}