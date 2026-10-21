// src/types/crm.types.ts

export type CustomerType   = 'LEAD' | 'CUSTOMER'
export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
export type ActivityType =
  | 'CREATED' | 'UPDATED' | 'DELETED' | 'RESTORED'
  | 'STATUS_CHANGED' | 'TAG_ADDED' | 'TAG_REMOVED'
  | 'NOTE_ADDED' | 'BOOKING_LINKED' | 'INVOICE_LINKED' | 'QUOTE_LINKED'
  | 'STAGE_CHANGED' | 'OWNER_CHANGED' | 'DEAL_UPDATED' | 'LEAD_CONVERTED' | 'LEAD_CONVERSION_UNDONE'

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
  ownerId?:           string | null
  dealValue?:         number | null
  expectedCloseDate?: string | null
  leadSource?:        LeadSource | null
  customFields?:      Record<string, string>
  pipelineStage?:     'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST' | null
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

export type LeadSource = 'WEBSITE' | 'REFERRAL' | 'WALK_IN' | 'PHONE' | 'EMAIL' | 'SOCIAL_MEDIA' | 'ADVERT' | 'EVENT' | 'PARTNER' | 'OTHER'

export interface CreateCustomerRequest {
  name:          string
  email?:        string
  phone?:        string
  address?:      Partial<Address>
  taxNumber?:    string
  notes?:        string
  customerType?: CustomerType
  leadSource?:   LeadSource
  customFields?: Record<string, string>
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
  leadSource?:   LeadSource
  customFields?: Record<string, string>
  status?:       CustomerStatus
}

/** Same shape as PageResponse; the customer screens use this name. */
export interface SpringPage<T> {
  content:       T[]
  totalElements: number
  totalPages:    number
  number:        number
}

export interface PageResponse<T> {
  content:       T[]
  totalElements: number
  totalPages:    number
  number:        number
  size:          number
}
