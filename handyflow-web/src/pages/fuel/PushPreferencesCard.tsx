import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnPrimary, card } from "../warehousing/whseUi"
import { PUSH_TYPES, unwrap } from "./opsPolicyOps"

const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

/** The signed-in person's own choice of which Fuel alerts reach their phone. Everyone with Fuel access can change their own; nobody else's. */
export default function PushPreferencesCard() {
  const allowed = usePermissions(["FUEL_READ", "FUEL_DRIVER", "FUEL_MANAGE"])
  const qc = useQueryClient()
  const q = useQuery<Record<string, boolean>>({ queryKey: ["fuel-push-prefs"], retry: false, enabled: allowed, queryFn: async () => (unwrap(await apiClient.get("/api/v1/fuel/push/preferences")) as Record<string, boolean> | null) ?? {} })
  const [on, setOn] = useState<Record<string, boolean>>({}); const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) setOn(q.data) }, [q.data])
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/push/preferences", { enabled: on }),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-push-prefs"] }) }, onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save")) },
  })
  if (!allowed) return null
  return (
    <section style={{ ...card, padding: 16, marginBottom: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>My alerts</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>Which Fuel alerts reach your phone. This is only your own choice; who is told about each kind of alert is set under Settings.</p>
      {q.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{msg(q.error, "Could not load your alert choices")}</p>}
      {PUSH_TYPES.map(p => (
        <label key={p.type} style={{ display: "block", fontSize: 13, margin: "4px 0" }}>
          <input type="checkbox" aria-label={`Alert: ${p.label}`} checked={on[p.type] !== false} onChange={e => { setSaved(false); setOn(x => ({ ...x, [p.type]: e.target.checked })) }} /> {p.label}
        </label>
      ))}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      {saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved.</p>}
      <button style={{ ...btnPrimary, marginTop: 8 }} disabled={save.isPending} onClick={() => save.mutate()}>Save my choices</button>
    </section>
  )
}
