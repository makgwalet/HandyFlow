// src/types/crm.types.ts

export interface Address {
  street?: string
  suburb?: string
  city?: string
  province?: string
  postalCode?: string
}

export interface Customer {
  id: string
  name: string
  email: string
  phone: string
  address: Address
  taxNumber: string
  notes: string
  createdAt: string
}

export interface CreateCustomerRequest {
  name: string
  email?: string
  phone?: string
  address?: Address
  taxNumber?: string
  notes?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}