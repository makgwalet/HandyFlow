// src/types/billing.types.ts

export interface Plan {
  id: string
  name: string
  displayName: string
  description: string
  priceInRands: number
  maxUsers: number
  includedModuleCount: number
  includedModules: string[]
  features: Record<string, unknown>
}

export interface Subscription {
  id: string
  planName: string
  planDisplayName: string
  status: 'PILOT' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED'
  pilotEndsAt?: string
  pilotDaysRemaining?: number
  currentPeriodEnd: string
  priceInRands: number
}