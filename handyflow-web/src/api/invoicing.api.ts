// src/api/invoicing.api.ts

import { apiClient } from './client'
import type { Quote, CreateQuoteRequest, AddLineItemRequest } from '../types/invoicing.types'
import type { PageResponse } from '../types/crm.types'

export const invoicingApi = {
  getQuotes: async (params?: { page?: number; size?: number })
    : Promise<PageResponse<Quote>> => {
    const res = await apiClient.get('/api/v1/invoicing/quotes', { params })
    return res.data
  },

  getQuote: async (id: string): Promise<Quote> => {
    const res = await apiClient.get(`/api/v1/invoicing/quotes/${id}`)
    return res.data
  },

  createQuote: async (data: CreateQuoteRequest): Promise<Quote> => {
    const res = await apiClient.post('/api/v1/invoicing/quotes', data)
    return res.data
  },

  addLineItem: async (quoteId: string, data: AddLineItemRequest): Promise<Quote> => {
    const res = await apiClient.post(`/api/v1/invoicing/quotes/${quoteId}/line-items`, data)
    return res.data
  },

  sendQuote: async (quoteId: string): Promise<Quote> => {
    const res = await apiClient.post(`/api/v1/invoicing/quotes/${quoteId}/send`)
    return res.data
  },

  acceptQuote: async (quoteId: string): Promise<Quote> => {
    const res = await apiClient.post(`/api/v1/invoicing/quotes/${quoteId}/accept`)
    return res.data
  },

  convertToInvoice: async (quoteId: string): Promise<string> => {
    const res = await apiClient.post(`/api/v1/invoicing/quotes/${quoteId}/convert-to-invoice`)
    return res.data
  },
}