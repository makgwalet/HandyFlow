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
  // FIX (identity module modernization): the backend's AuthResponse record
  // has always returned this (see AuthService.buildAuthResponse()) and
  // LoginPage.tsx has always read it (`data.subscriptionStatus === 'SUSPENDED'`)
  // to decide whether to redirect to the account-locked screen — but it was
  // never declared here, so that read was a real TypeScript error
  // (confirmed via `tsc -b`: "Property 'subscriptionStatus' does not exist
  // on type 'AuthResponse'") silently ignored because JS doesn't enforce
  // types at runtime. Optional/nullable because the backend itself can
  // return null here (see AuthService's defensive try/catch around
  // resolving it, and UserController's acceptInvitation() flow, which reads
  // Tenant.status directly rather than the richer Subscription status).
  subscriptionStatus?: string | null
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
