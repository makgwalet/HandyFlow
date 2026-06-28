// src/types/crm.types.ts

export type CustomerType   = 'LEAD' | 'CUSTOMER'
export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
export type ActivityType =
  | 'CREATED' | 'UPDATED' | 'DELETED' | 'RESTORED'
  | 'STATUS_CHANGED' | 'TAG_ADDED' | 'TAG_REMOVED'
  | 'NOTE_ADDED' | 'BOOKING_LINKED' | 'INVOICE_LINKED' | 'QUOTE_LINKED'

export interface Address {
  street?:     string
  suburb?:     string
  city?:       string
  province?:   string
  postalCode?: string
}

export interface Customer {
  id:           string
  name:         string
  email?:       string
  phone?:       string
  address?:     Address
  taxNumber?:   string
  notes?:       string
  customerType: CustomerType
  status:       CustomerStatus
  tags:         string[]
  createdAt:    string
  updatedAt:    string
}

export interface CustomerActivity {
  id:           string
  activityType: ActivityType
  payload?:     Record<string, unknown>
  note?:        string
  performedBy?: string
  createdAt:    string
}

export interface CreateCustomerRequest {
  name:          string
  email?:        string
  phone?:        string
  address?:      Partial<Address>
  taxNumber?:    string
  notes?:        string
  customerType?: CustomerType
  tags?:         string[]
}

export interface UpdateCustomerRequest {
  name:          string
  email?:        string
  phone?:        string
  address?:      Partial<Address>
  taxNumber?:    string
  notes?:        string
  customerType?: CustomerType
  status?:       CustomerStatus
}

export interface SpringPage<T> {
  content:       T[]
  totalElements: number
  totalPages:    number
  number:        number
}
