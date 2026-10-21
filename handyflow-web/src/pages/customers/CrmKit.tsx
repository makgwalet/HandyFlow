// src/pages/customers/CrmKit.tsx — the small set of state components every CRM panel shares, so loading,
// error and empty look the same everywhere. Styles live in crm.css (scoped to .crm-scope).
import type { ReactNode } from "react"
import { AlertCircle, Inbox } from "lucide-react"

/** Skeleton placeholder. The text stays in the DOM for screen readers (and tests). */
export function Loading({ label = "Loading…" }: { label?: string }) {
  return <div className="crm-skel" aria-live="polite" aria-busy="true"><i /><i /><i /><span className="crm-sr">{label}</span></div>
}

/** Error box; always role="alert". */
export function ErrorNote({ children }: { children: ReactNode }) {
  return <div role="alert" className="crm-state crm-state--error"><AlertCircle size={16} aria-hidden style={{ flexShrink: 0 }} /><span>{children}</span></div>
}

/** Empty state with a headline and an optional hint. */
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="crm-state crm-state--empty"><Inbox size={22} aria-hidden /><strong>{title}</strong>{children && <span>{children}</span>}</div>
}
