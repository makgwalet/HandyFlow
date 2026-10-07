// One modal for the clinic screens: accessible (role, label, Escape, focus in and back out),
// stops the page behind from scrolling, keeps the header and footer in view while the body scrolls,
// and does not close on a stray backdrop click (a form can hold a lot of typing).
import { useEffect, useId, useRef, type ReactNode } from "react"
import { X } from "lucide-react"

export interface ModalShellProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** Pinned under the body, so the primary action never scrolls out of reach. */
  footer?: ReactNode
  /** Optional small icon/tile shown before the title. */
  icon?: ReactNode
  width?: number
}

export default function ModalShell({ title, onClose, children, footer, icon, width = 520 }: ModalShellProps) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useEffect(() => {
    const before = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = "hidden"
    // Focus the first field, else the panel, so the keyboard starts inside the dialog.
    const first = panelRef.current?.querySelector<HTMLElement>("input:not([type=hidden]), select, textarea")
    ;(first ?? panelRef.current)?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" && !e.defaultPrevented) closeRef.current() }
    document.addEventListener("keydown", onKey)
    return () => {
      document.removeEventListener("keydown", onKey)
      document.body.style.overflow = overflow
      before?.focus?.()
    }
  }, [])

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center",
      justifyContent: "center", zIndex: 1200, backdropFilter: "blur(3px)", padding: 16 }}>
      <div ref={panelRef} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}
        style={{ background: "var(--hf-surface)", borderRadius: 16, width, maxWidth: "100%", maxHeight: "92vh", display: "flex",
          flexDirection: "column", boxShadow: "0 24px 64px rgba(0,0,0,0.22)", outline: "none" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, padding: "18px 24px",
          borderBottom: "1px solid var(--hf-border-subtle)" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0 }}>
            {icon}
            <h3 id={titleId} style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
          </div>
          <button aria-label="Close" onClick={onClose}
            style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", display: "flex", padding: 4 }}><X size={20} /></button>
        </div>
        <div style={{ padding: "20px 24px", overflowY: "auto", flex: 1 }}>{children}</div>
        {footer && <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", alignItems: "center", padding: "14px 24px",
          borderTop: "1px solid var(--hf-border-subtle)" }}>{footer}</div>}
      </div>
    </div>
  )
}
