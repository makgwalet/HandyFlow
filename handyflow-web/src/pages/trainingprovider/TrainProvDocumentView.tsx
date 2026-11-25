// The document as it would be handed over. The disclaimer is always shown with it. Print uses the browser's own print.
import { card, ghost, muted } from "./tpStyle"
import { DOC_TYPES, label, type LearnerDocument } from "./documentRules"

export default function TrainProvDocumentView({ doc, onClose }: { doc: LearnerDocument; onClose: () => void }) {
  return (
    <div style={{ ...card, display: "grid", gap: 10, background: "#fff" }}>
      <div style={muted}>{label(DOC_TYPES, doc.docType)} · {doc.documentNo}{doc.status === "VOID" ? " · VOID" : ""}</div>
      <h3 style={{ margin: 0 }}>{doc.title}</h3>
      <div>{doc.body}</div>
      <div style={muted}>Issued on {doc.issuedOn}</div>
      <div style={{ borderTop: "1px solid #ddd", paddingTop: 8, fontSize: 13 }}>{doc.disclaimer}</div>
      <div style={{ display: "flex", gap: 8 }}>
        <button style={ghost} onClick={() => window.print()}>Print</button>
        <button style={ghost} onClick={onClose}>Close</button>
      </div>
    </div>
  )
}
