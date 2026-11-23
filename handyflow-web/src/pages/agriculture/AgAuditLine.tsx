// src/pages/agriculture/AgAuditLine.tsx
//
// "Added by Thandi on 2026-11-02 · Last changed by Sipho on 2026-11-20" under a record's heading. Read-only; shows nothing
// while loading, on an error, or when nothing is known, so it never gets in the way of the screen.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { auditText, type AuditInfo, type AuditType } from "./agAudit.logic"

export default function AgAuditLine({ type, id }: { type: AuditType; id: string }) {
  const { data } = useQuery<AuditInfo>({
    queryKey: ["ag-audit", type, id], retry: false, staleTime: 30_000,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/audit/${type}/${id}`)).data,
  })
  const text = auditText(data)
  return text ? <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "2px 0 0" }}>{text}</p> : null
}
