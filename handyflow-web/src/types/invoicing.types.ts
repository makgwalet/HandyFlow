// src/types/invoicing.types.ts

export interface LineItem {
  id: string
  catalogueItemId?: string
  description: string
  unit: string
  quantity: number
  unitPrice: number
  vatRate: number
  lineTotal: number
  vatAmount: number
}

export interface Quote {
  id: string
  quoteNumber: string
  status: 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'INVOICED'
  customerId: string
  title: string
  notes?: string
  subtotal: number
  vatTotal: number
  total: number
  currency: string
  sentAt?: string
  expiresAt?: string
  acceptedAt?: string
  lineItems: LineItem[]
  createdAt: string
}

export interface CreateQuoteRequest {
  customerId: string
  title: string
  notes?: string
}

export interface AddLineItemRequest {
  catalogueItemId?: string
  description: string
  unit: string
  quantity: number
  unitPrice: number
  vatRate?: number
}