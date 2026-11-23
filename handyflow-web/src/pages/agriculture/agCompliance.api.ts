// src/pages/agriculture/agCompliance.api.ts
// Is this animal, group or crop held (withdrawal period / pre-harvest interval) on a given day?
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import type { ProduceWithdrawal, WithdrawalStatus } from "./agCompliance.logic"

export type HoldKind = "animal" | "group" | "crop"
const BASE = "/api/v1/agriculture"
const path = (kind: HoldKind, id: string) =>
  kind === "animal" ? `${BASE}/animals/${id}/withdrawal` : kind === "group" ? `${BASE}/groups/${id}/withdrawal` : `${BASE}/crop-cycles/${id}/pre-harvest`

/** Held on {asOf}? Only asks while {enabled}, and again whenever the day changes. */
export function useWithdrawal(kind: HoldKind, id: string, asOf: string, enabled: boolean) {
  return useQuery<WithdrawalStatus>({
    queryKey: ["ag", "withdrawal", kind, id, asOf],
    enabled: enabled && !!id && !!asOf,
    queryFn: async () => (await apiClient.get(path(kind, id), { params: { asOf } })).data as WithdrawalStatus,
  })
}

/** Milk and egg holds for an animal or group on {asOf}. Informational: the module does not yet record milk or egg sales. */
export function useProduceWithdrawal(kind: "animal" | "group", id: string, asOf: string) {
  return useQuery<ProduceWithdrawal>({
    queryKey: ["ag", "produce-withdrawal", kind, id, asOf],
    enabled: !!id && !!asOf,
    queryFn: async () => (await apiClient.get(`${BASE}/${kind === "animal" ? "animals" : "groups"}/${id}/produce-withdrawal`, { params: { asOf } })).data as ProduceWithdrawal,
  })
}
