// Clients and insurers as flat lists, for the dropdowns and for turning ids into names.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { list } from "../projects/tabs/pmKit"

export interface BClient { id: string; clientName: string; clientType: string; registrationOrIdNumber: string | null; contactName: string | null; contactEmail: string | null; contactPhone: string | null; address: string | null; defaultCommissionRatePct: number | null; notes: string | null; active: boolean }
export interface BInsurer { id: string; name: string; contactName: string | null; contactEmail: string | null; contactPhone: string | null; notes: string | null; active: boolean }
export const BASE = "/api/v1/insurance-brokerage"

export function useLookups() {
  const clients = useQuery<BClient[]>({ queryKey: ["ib-clients-all"], queryFn: async () => list<BClient>(await apiClient.get(`${BASE}/clients/all`)) })
  const insurers = useQuery<BInsurer[]>({ queryKey: ["ib-insurers-all"], queryFn: async () => list<BInsurer>(await apiClient.get(`${BASE}/insurers/all`)) })
  const clientName = (id: string) => clients.data?.find(c => c.id === id)?.clientName ?? "…"
  const insurerName = (id: string) => insurers.data?.find(i => i.id === id)?.name ?? "…"
  return { clients: clients.data ?? [], insurers: insurers.data ?? [], clientName, insurerName }
}

export { pageOf } from "../insurance/parts"
