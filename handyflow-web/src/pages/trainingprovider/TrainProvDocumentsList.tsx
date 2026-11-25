// Documents the provider has issued, newest first. Void documents stay in the list.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { DOCUMENT_NOTE, DOC_TYPES, docTone, documentLine, label, type LearnerDocument } from "./documentRules"
import { Badge } from "./tpUi"
import { card, input, muted, unwrap } from "./tpStyle"
import { DOC } from "./tpApi"

export default function TrainProvDocumentsList() {
  const [docType, setDocType] = useState("")
  const [status, setStatus] = useState("ISSUED")
  const { data = [], isLoading } = useQuery<LearnerDocument[]>({
    queryKey: ["tp-documents", "all", docType, status],
    queryFn: async () => unwrap<LearnerDocument[]>(await apiClient.get(DOC, { params: { docType: docType || undefined, status: status || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Document type filter" style={{ ...input, width: "auto" }} value={docType} onChange={e => setDocType(e.target.value)}><option value="">All types</option>{Object.entries(DOC_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <select aria-label="Document status filter" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}><option value="ISSUED">Issued</option><option value="VOID">Void</option><option value="">All</option></select>
      </div>
      <div style={muted}>{DOCUMENT_NOTE}</div>
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No documents found.</div> : data.map(d => (
        <div key={d.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{d.learnerName} · {d.programmeTitle}</strong><div style={muted}>{d.documentNo} · {label(DOC_TYPES, d.docType)} · {documentLine(d)}</div></div>
            <Badge text={d.status === "VOID" ? "Void" : "Issued"} tone={docTone(d)} />
          </div>
        </div>
      ))}
    </div>
  )
}
