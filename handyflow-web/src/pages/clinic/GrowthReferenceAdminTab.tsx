// Review and activation of growth reference sets (CLINIC-DEC-019). Loading the L, M and S numbers is still done through the API.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import ModalShell from "./ModalShell"
import { BORDER, GRAY } from "./patientFile.shared"
import { ACTION_LABEL, ACTION_WARNING, actionsFor, ageRange, measureLabel, order, sexLabel, statusLabel, type RefAction, type RefSet } from "./growthReferenceView"

const unwrap = (r: any) => r.data?.data ?? r.data

export default function GrowthReferenceAdminTab() {
  const qc = useQueryClient()
  const admin = usePermission("CLINIC_CONTENT_ADMIN")
  const approve = usePermission("CLINIC_CONTENT_APPROVE")
  const [pending, setPending] = useState<{ set: RefSet; action: RefAction } | null>(null)
  const [error, setError] = useState("")

  const q = useQuery<RefSet[]>({
    queryKey: ["clinic-growth-reference"], retry: false,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/growth-reference")) ?? [],
  })
  const step = useMutation({
    mutationFn: ({ set, action }: { set: RefSet; action: RefAction }) => apiClient.post(`/api/v1/clinic/growth-reference/${set.id}/${action}`),
    onSuccess: () => { setPending(null); setError(""); qc.invalidateQueries({ queryKey: ["clinic-growth-reference"] }); qc.invalidateQueries({ queryKey: ["clinic-growth"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "That step was refused."),
  })

  const rows = order(q.data ?? [])
  return (
    <div>
      <div style={{ fontWeight: 700 }}>Growth reference sets</div>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 12, maxWidth: 640 }}>
        Growth charts show z-scores and percentiles only from an active set. A set is checked against its source by a clinical reviewer, and activated by someone else.
        Until then charts say the data is not clinically approved.
      </div>
      {q.isLoading && <div style={{ fontSize: 13, color: GRAY }}>Loading…</div>}
      {q.isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The reference sets could not be loaded.</div>}
      {q.data && rows.length === 0 && <div style={{ fontSize: 13, color: GRAY }}>No reference sets have been loaded yet.</div>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: GRAY, fontSize: 11 }}>
            {["Measure", "For", "Source", "Ages", "Rows", "Status", ""].map(h => <th key={h} style={{ padding: "6px 8px" }}>{h}</th>)}
          </tr></thead>
          <tbody>{rows.map(s => (
            <tr key={s.id} style={{ borderTop: `1px solid ${BORDER}` }}>
              <td style={{ padding: "8px" }}>{measureLabel(s.measure)}<div style={{ fontSize: 11, color: GRAY }}>{s.title}</div></td>
              <td style={{ padding: "8px" }}>{sexLabel(s.sex)}</td>
              <td style={{ padding: "8px" }}>{s.source}<div style={{ fontSize: 11, color: GRAY }}>{s.sourceVersion}</div></td>
              <td style={{ padding: "8px" }}>{ageRange(s)}</td>
              <td style={{ padding: "8px" }}>{s.pointCount}</td>
              <td style={{ padding: "8px", fontWeight: 600 }}>{statusLabel(s.status)}</td>
              <td style={{ padding: "8px", whiteSpace: "nowrap" }}>
                {actionsFor(s, { admin, approve }).map(a => (
                  <button key={a} type="button" onClick={() => { setError(""); setPending({ set: s, action: a }) }}
                    style={{ marginRight: 6, padding: "4px 10px", border: `1px solid ${BORDER}`, borderRadius: 7, background: "var(--hf-surface)", cursor: "pointer", fontSize: 12 }}>
                    {ACTION_LABEL[a]}
                  </button>
                ))}
              </td>
            </tr>))}
          </tbody>
        </table>
      )}
      {pending && (
        <ModalShell title={ACTION_LABEL[pending.action]} onClose={() => { setPending(null); setError("") }} width={460}
          footer={<>
            <button type="button" onClick={() => { setPending(null); setError("") }} style={{ padding: "9px 18px", border: `1px solid ${BORDER}`, borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
            <button type="button" disabled={step.isPending} onClick={() => step.mutate(pending)}
              style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: step.isPending ? 0.6 : 1 }}>
              {step.isPending ? "Saving..." : ACTION_LABEL[pending.action]}
            </button></>}>
          <div style={{ fontSize: 13, marginBottom: 8 }}><b>{measureLabel(pending.set.measure)}</b> · {sexLabel(pending.set.sex)} · {pending.set.source} {pending.set.sourceVersion}</div>
          <div style={{ fontSize: 13, color: GRAY }}>{ACTION_WARNING[pending.action] ?? "The set moves to the next step."}</div>
          {error && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
        </ModalShell>
      )}
    </div>
  )
}
