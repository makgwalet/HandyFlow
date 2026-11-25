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
