// src/types/auth.types.ts

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: string
  tenantId: string
  email: string
  firstName: string
  lastName: string
  permissions: string[]
}

export interface RegisterRequest {
  companyName: string
  slug: string
  firstName: string
  lastName: string
  email: string
  password: string
  moduleKeys?: string[]
}

export interface LoginRequest {
  email: string
  password: string
  tenantSlug: string
}

export interface User {
  userId: string
  tenantId: string
  email: string
  firstName: string
  lastName: string
  permissions: string[]
}
