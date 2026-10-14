// The client every per-client Bookkeeping screen works on. It lives in the address (?client=<id>), so a link from the work queue
// opens the right client and a refresh keeps it.
import type { ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { useSearchParams } from "react-router-dom"
import { apiClient } from "../../api/client"
import { rowsOf, type Client } from "./bookkeeping.logic"
import { field, muted } from "./ui"

export function useClients() {
  return useQuery<Client[]>({ queryKey: ["bk-clients"], queryFn: async () => rowsOf<Client>(await apiClient.get("/api/v1/bookkeeping/clients?size=200&sort=tradingName")) })
}

export default function ClientScope({ children }: { children: (clientId: string, client: Client | undefined) => ReactNode }) {
  const [params, setParams] = useSearchParams()
  const clientId = params.get("client") ?? ""
  const { data: clients = [], isLoading } = useClients()
  const client = clients.find(c => c.id === clientId)
  return (
    <div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 14, fontSize: 12 }}>
        <label>Client{" "}
          <select aria-label="Client" value={clientId} onChange={e => setParams(e.target.value ? { client: e.target.value } : {})} style={{ ...field, minWidth: 260 }}>
            <option value="">Choose a client…</option>
            {clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}{c.status === "ACTIVE" ? "" : " (inactive)"}</option>)}
          </select>
        </label>
      </div>
      {!clientId && <p style={muted}>{isLoading ? "Loading…" : clients.length === 0 ? "No clients yet. Add one under Clients." : "Choose a client to work on."}</p>}
      {clientId && children(clientId, client)}
    </div>
  )
}
