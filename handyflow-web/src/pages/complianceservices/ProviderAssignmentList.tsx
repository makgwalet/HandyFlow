// src/pages/complianceservices/ProviderAssignmentList.tsx
//
// A list of provider work. Each row opens to the full card, which is fetched when opened so deliverables are current.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import ProviderAssignmentCard from "./ProviderAssignmentCard"
import { assignmentStatus, type Assignment, type AssignmentDetail } from "./providerRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T

function Opened({ id, showClient }: { id: string; showClient?: boolean }) {
  const { data, isLoading } = useQuery<AssignmentDetail>({
    queryKey: ["cs-prov-assign-detail", id],
    queryFn: async () => unwrap<AssignmentDetail>(await apiClient.get(`/api/v1/compliance-services/provider-assignments/${id}`)),
    staleTime: 0,
  })
  if (isLoading || !data) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>
  return <ProviderAssignmentCard initial={data} showClient={showClient} />
}

export default function ProviderAssignmentList({ items, showClient, empty }: { items: Assignment[]; showClient?: boolean; empty: string }) {
  const [openId, setOpenId] = useState<string | null>(null)
  if (items.length === 0) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>{empty}</div>
  return (
    <div style={{ display: "grid", gap: 8 }}>
      {items.map(a => (
        <div key={a.id}>
          <button onClick={() => setOpenId(openId === a.id ? null : a.id)} aria-expanded={openId === a.id}
            style={{ width: "100%", textAlign: "left", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, cursor: "pointer" }}>
            <strong>{a.title}</strong> · {a.providerName ?? "Provider"}{showClient && a.clientName ? ` · ${a.clientName}` : ""} · {assignmentStatus(a.status)}{a.dueDate ? ` · due ${a.dueDate}` : ""}
          </button>
          {openId === a.id && <div style={{ marginTop: 6 }}><Opened id={a.id} showClient={showClient} /></div>}
        </div>
      ))}
    </div>
  )
}
