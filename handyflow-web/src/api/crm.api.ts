// src/api/crm.api.ts

import { apiClient } from './client'
import type { Customer, CreateCustomerRequest, PageResponse } from '../types/crm.types'

export const crmApi = {
  getCustomers: async (params?: { search?: string; page?: number; size?: number })
    : Promise<PageResponse<Customer>> => {
    const res = await apiClient.get('/api/v1/crm/customers', { params })
    return res.data
  },

  getCustomer: async (id: string): Promise<Customer> => {
    const res = await apiClient.get(`/api/v1/crm/customers/${id}`)
    return res.data
  },

  createCustomer: async (data: CreateCustomerRequest): Promise<Customer> => {
    const res = await apiClient.post('/api/v1/crm/customers', data)
    return res.data
  },

  deleteCustomer: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/v1/crm/customers/${id}`)
  },
}