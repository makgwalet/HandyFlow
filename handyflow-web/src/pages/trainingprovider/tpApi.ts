// Shared queries for the accreditation and catalogue screens.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import type { Authority, Site } from "./accreditationRules"
import { unwrap } from "./tpStyle"

export const CAT = "/api/v1/training-provider/catalogue"
export const ACC = "/api/v1/training-provider/accreditation"

export function useAuthorities() {
  return useQuery<Authority[]>({ queryKey: ["tp-authorities"], queryFn: async () => unwrap<Authority[]>(await apiClient.get(`${ACC}/authorities`)) })
}
export function useSites() {
  return useQuery<Site[]>({ queryKey: ["tp-sites"], queryFn: async () => unwrap<Site[]>(await apiClient.get(`${ACC}/sites`)) })
}

export type ProgrammeOption = { id: string; code: string; title: string; trainingActivity: string }
export function useActiveProgrammes() {
  return useQuery<ProgrammeOption[]>({
    queryKey: ["tp-programmes-active"],
    queryFn: async () => unwrap<ProgrammeOption[]>(await apiClient.get(`${CAT}/programmes`, { params: { status: "ACTIVE" } })),
  })
}

export type ClientOption = { id: string; tradingName: string }
export function useClients() {
  return useQuery<ClientOption[]>({
    queryKey: ["tp-client-options"],
    queryFn: async () => {
      const d = unwrap<ClientOption[] | { content?: ClientOption[] }>(await apiClient.get("/api/v1/training-provider/clients", { params: { size: 200 } }))
      return Array.isArray(d) ? d : d?.content ?? []
    },
  })
}

export const LRN = "/api/v1/training-provider"

export const PRC = "/api/v1/training-provider/practitioners"

export const ASM = "/api/v1/training-provider/assessment-instruments"
