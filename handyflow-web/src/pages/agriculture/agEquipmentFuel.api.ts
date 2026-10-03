// src/pages/agriculture/agEquipmentFuel.api.ts
//
// Costing machine use (from Fleet) and allocating own fuel (from Fuel) into the cost ledger (ADR-001, W4). Equipment needs AGRICULTURE_FINANCE
// and FLEET_READ; fuel needs AGRICULTURE_FINANCE and FUEL_MARGIN_READ, the permission Fuel itself uses for cost data.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { api, useAgMutation } from "./agCrops.api"
import type { TargetType } from "./agLedger.api"

export interface EquipmentOption {
  vehicleId: string; registration: string; description: string
  engineHours: number | null
  operatingRatePerHour: number | null   // service and repairs only; null until set in Fleet
}

export interface AllocationLine { targetType: TargetType; targetId: string; percentage: number }

export interface CostEquipmentBody { vehicleId: string; workDate: string; hours: number; notes?: string; allocations: AllocationLine[] }

export interface FuelDispatchRow {
  dispatchId: string; date: string; tankName: string | null; vehicleId: string | null; vehicle: string; recipientName: string | null
  litres: number; costPerLitre: number | null; cost: number | null; hoursReading: number | null
}

export interface FuelOverview { from: string; to: string; dispatches: FuelDispatchRow[]; alreadyAllocated: number }

export interface AllocateFuelBody { dispatchId: string; notes?: string; allocations: AllocationLine[] }

const BASE = "/api/v1/agriculture"
const AG = ["ag", "equipmentFuel"] as const
const LEDGER = ["ag", "ledger"] as const

export function useEquipment(enabled = true) {
  return useQuery<EquipmentOption[]>({ queryKey: [...AG, "equipment"], enabled, queryFn: async () => (await apiClient.get(`${BASE}/finance/equipment`)).data })
}

export function useCostEquipment(farmId: string) {
  return useAgMutation((body: CostEquipmentBody) => api.post(`/farms/${farmId}/equipment/cost`, body),
    { invalidate: [[...LEDGER]], success: "Machine use costed into the ledger", failure: "Couldn't cost the machine use." })
}

export function useUnallocatedFuel(farmId: string, from: string, to: string, enabled = true) {
  return useQuery<FuelOverview>({
    queryKey: [...AG, "fuel", farmId, from, to], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/fuel/unallocated`, { params: { from, to } })).data,
  })
}

export function useAllocateFuel(farmId: string) {
  return useAgMutation((body: AllocateFuelBody) => api.post(`/farms/${farmId}/fuel/allocate`, body),
    { invalidate: [[...AG, "fuel"], [...LEDGER]], success: "Fuel allocated to the ledger", failure: "Couldn't allocate the fuel." })
}
