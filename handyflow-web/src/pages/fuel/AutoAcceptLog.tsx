import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card } from "../warehousing/whseUi"
import { acceptLogLine, onlyAcceptLog, type AcceptLogRow } from "./shiftControlOps"

const unwrap = (r: any) => r.data?.data ?? r.data

/** Every shift the system accepted on its own: the variance, the tolerance in force, and when. */
export default function AutoAcceptLog() {
  const q = useQuery<AcceptLogRow[]>({ queryKey: ["fuel-auto-accept-log"], queryFn: async () => onlyAcceptLog(unwrap(await apiClient.get("/api/v1/fuel/import-auto-accept/log"))) })
  const rows = q.data ?? []
  if (rows.length === 0) return null
  return (
    <div style={{ ...card, padding: 16, marginTop: 14 }}>
      <h3 style={{ margin: "0 0 8px", fontSize: 14 }}>Shifts accepted automatically</h3>
      {rows.map(r => <div key={r.id} style={{ fontSize: 12.5, padding: "3px 0", borderTop: "1px solid var(--hf-border)" }}>{acceptLogLine(r)}</div>)}
    </div>
  )
}
