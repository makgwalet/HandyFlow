// src/pages/security/GuardFaceCard.tsx
//
// Face data on the guard's Overview tab: whether a template is held and since when the guard consented, and (for managers) removal,
// which is how a guard withdraws consent. Removal is two steps, and the server audits it.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ScanFace } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { REMOVE_WARNING, readStatus, statusLabel, statusLine, statusTone, type FaceStatus } from "./faceLogic"
import { useCompanyZone } from "./companyZone"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "7px 11px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const danger: React.CSSProperties = { ...btn, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text-strong)", borderColor: "var(--hf-danger-text-strong)" }
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

export default function GuardFaceCard({ guardId, canManage }: { guardId: string; canManage: boolean }) {
  useCompanyZone()
  const qc = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [err, setErr] = useState("")
  const { data } = useQuery<FaceStatus | null>({
    queryKey: ["guard-face", guardId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/face`); return readStatus(r.data?.data ?? r.data) },
    retry: false,
  })
  const remove = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/security/guards/${guardId}/face`),
    onSuccess: () => { setErr(""); setConfirming(false); qc.setQueryData<FaceStatus>(["guard-face", guardId], { stored: false, consentAt: null }) },
    onError: e => { setConfirming(false); setErr(errText(e)) },
  })

  if (!data) return null
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <div>
          <div style={{ fontWeight: 800, marginBottom: 4, display: "flex", gap: 8, alignItems: "center" }}><ScanFace size={16} /> Face data <Chip tone={statusTone(data)}>{statusLabel(data)}</Chip></div>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{statusLine(data)}</div>
        </div>
        {canManage && data.stored && !confirming && <button type="button" style={btn} onClick={() => setConfirming(true)}>Remove face data</button>}
      </div>
      {confirming && (
        <div role="alertdialog" aria-label="Confirm removal of face data" style={{ marginTop: 10, display: "grid", gap: 8 }}>
          <div style={{ fontSize: 13 }}>{REMOVE_WARNING}</div>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" style={danger} disabled={remove.isPending} onClick={() => remove.mutate()}>Yes, delete it</button>
            <button type="button" style={btn} onClick={() => setConfirming(false)}>Keep it</button>
          </div>
        </div>
      )}
      {err && <div role="alert" style={{ marginTop: 8, fontSize: 12, color: "var(--hf-danger-text)" }}>{err}</div>}
    </div>
  )
}
