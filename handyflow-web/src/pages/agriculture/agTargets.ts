// src/pages/agriculture/agTargets.ts
// The production targets a cost (W1) or a sale (W2) can be attributed to on one farm: its crop cycles, groups, animals and enterprises.
import { useCropCycles, useCropTypes, useEnterprises } from "./agCrops.api"
import { cycleLabel } from "./agCrops.logic"
import { useFarmAnimals, useFarmGroups, type TargetType } from "./agLedger.api"

export interface TargetOption { type: TargetType; id: string; label: string }

export function useTargetOptions(farmId: string): TargetOption[] {
  const cycles = useCropCycles(farmId).data ?? []
  const cropTypes = useCropTypes().data ?? []
  const groups = useFarmGroups(farmId).data ?? []
  const animals = useFarmAnimals(farmId).data ?? []
  const enterprises = useEnterprises(farmId).data ?? []
  return [
    ...cycles.map(c => ({ type: "CROP_CYCLE" as const, id: c.id, label: cycleLabel(c, cropTypes) })),
    ...groups.map(g => ({ type: "GROUP" as const, id: g.id, label: g.batchNumber })),
    ...animals.map(a => ({ type: "ANIMAL" as const, id: a.id, label: a.name ? `${a.tagNumber} (${a.name})` : a.tagNumber })),
    ...enterprises.map(e => ({ type: "ENTERPRISE" as const, id: e.id, label: e.name })),
  ]
}
