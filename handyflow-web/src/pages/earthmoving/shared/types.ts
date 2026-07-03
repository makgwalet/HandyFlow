// src/pages/earthmoving/shared/types.ts
//
// WHY this file exists: AssetsTab had a full Asset interface, MaintenanceTab
// and OperatorLogsTab each hand-rolled their own *partial* Asset interface
// with just the fields they used, and DeploymentsTab/IncidentsTab gave up
// and typed assets as `any[]`. That means five different shapes of "what an
// Asset looks like" living in five files, silently drifting out of sync as
// the backend changes — exactly the class of bug that Java's type system
// prevents but TypeScript only prevents if you actually share the type.

export interface Asset {
  id: string
  name: string
  fleetNumber: string | null
  assetType: string
  make: string | null
  model: string | null
  year: number | null
  serialNumber: string | null
  registration: string | null
  ownershipType: string // OWN | HIRED_IN | HIRED_OUT
  hireSupplier: string | null
  hireStartDate: string | null
  hireEndDate: string | null
  status: string // AVAILABLE | DEPLOYED | MAINTENANCE | BREAKDOWN | HIRED_OUT | RETIRED
  currentSite: string | null
  currentClient: string | null
  dailyRate: number | null
  hourlyRate: number | null
  currentHours: number
  lastServiceHours: number
  serviceIntervalHours: number
  dueForService: boolean
  notes: string | null
  createdAt: string
}

export interface MaintenanceRecord {
  id: string
  assetId: string
  type: string
  description: string
  performedAt: string
  hoursAtService: number | null
  cost: number | null
  supplier: string | null
  invoiceRef: string | null
  createdAt: string
}

export interface OperatorLog {
  id: string
  assetId: string
  operatorName: string | null
  siteName: string | null
  startedAt: string
  endedAt: string | null
  hoursLogged: number | null
  fuelUsedLitres: number | null
  createdAt: string
}

export interface Incident {
  id: string
  assetId: string
  type: string // BREAKDOWN | ACCIDENT | THEFT | FIRE | ROLLOVER | NEAR_MISS | FUEL_SPILL | OTHER
  severity: string // LOW | MEDIUM | HIGH | CRITICAL
  title: string
  description: string | null
  operatorName: string | null
  siteName: string | null
  latitude: number | null
  longitude: number | null
  status: string // OPEN | RESOLVED
  reportedAt: string
  resolvedAt: string | null
  resolutionNotes: string | null
}
