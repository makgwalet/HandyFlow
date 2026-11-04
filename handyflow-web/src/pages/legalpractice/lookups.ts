// Clients and attorneys as flat lists, for the dropdowns and for turning ids into names.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { pageOf } from "./kit"

export const BASE = "/api/v1/legal-practice"
export interface LClient { id: string; name: string; email: string | null; phone: string | null; clientType: string; idOrRegistrationNumber: string | null; trustBalance: number; status: string; notes: string | null }
export interface LAttorney { id: string; name: string; email: string | null; phone: string | null; role: string; admissionNumber: string | null; hourlyRate: number | null; employeeId: string | null; employeeName: string | null; active: boolean }

export function useLookups() {
  const clients = useQuery({ queryKey: ["lp-clients-all"], queryFn: async () => pageOf<LClient>(await apiClient.get(`${BASE}/clients`, { params: { size: 500 } })) })
  const attorneys = useQuery({ queryKey: ["lp-attorneys-all"], queryFn: async () => pageOf<LAttorney>(await apiClient.get(`${BASE}/attorneys`, { params: { size: 500 } })) })
  const cs = clients.data?.content ?? [], as = attorneys.data?.content ?? []
  return {
    clients: cs, attorneys: as, loading: clients.isLoading || attorneys.isLoading,
    clientName: (id: string) => cs.find(c => c.id === id)?.name ?? "…",
    attorneyName: (id: string) => as.find(a => a.id === id)?.name ?? "…",
    attorney: (id: string) => as.find(a => a.id === id),
  }
}
export type Lookups = ReturnType<typeof useLookups>
