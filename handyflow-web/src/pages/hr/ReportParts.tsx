// Small pieces the report screens share: a figure, a breakdown table, a list of notes.
import { card, td, th } from "./reports.shared"
import { groupText, type Group } from "./reports.logic"

export function Stat({ label, value, hint }: { label: string; value: React.ReactNode; hint?: string }) {
  return (
    <div style={{ ...card, padding: "12px 16px", minWidth: 150, flex: "1 1 150px" }}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700, marginTop: 2 }}>{value}</div>
      {hint && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>{hint}</div>}
    </div>
  )
}

export const StatRow = ({ children }: { children: React.ReactNode }) => (
  <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 18 }}>{children}</div>
)

export function Block({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ ...card, padding: "14px 16px", marginBottom: 16 }} aria-label={title}>
      <h3 style={{ margin: "0 0 10px", fontSize: 14 }}>{title}</h3>
      {children}
    </section>
  )
}

export function Breakdown({ title, groups, empty = "Nobody." }: { title: string; groups: Group[]; empty?: string }) {
  const total = groups.reduce((n, g) => n + (g.count ?? 0), 0)
  return (
    <Block title={title}>
      {groups.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{empty}</div> : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <tbody>{groups.map(g => (
            <tr key={g.label}>
              <td style={td}>{g.label}</td>
              <td style={{ ...td, textAlign: "right", width: 150, color: g.hidden ? "var(--hf-text-muted)" : undefined }}>{groupText(g)}</td>
              <td style={{ ...td, width: 120 }}>
                {!g.hidden && total > 0 && g.count !== null && (
                  <div aria-hidden style={{ height: 8, borderRadius: 4, background: "var(--hf-surface-sunken)" }}>
                    <div style={{ height: 8, borderRadius: 4, width: `${Math.round((g.count / total) * 100)}%`, background: "var(--hf-primary)" }} />
                  </div>
                )}
              </td>
            </tr>))}
          </tbody>
        </table>
      )}
    </Block>
  )
}

export function Notes({ notes }: { notes: string[] }) {
  if (notes.length === 0) return null
  return (
    <ul style={{ fontSize: 12, color: "var(--hf-text-muted)", paddingLeft: 18, margin: "8px 0 0" }} aria-label="Notes">
      {notes.map(n => <li key={n} style={{ marginBottom: 3 }}>{n}</li>)}
    </ul>
  )
}

export const Head = ({ cols, right = [] }: { cols: string[]; right?: number[] }) => (
  <thead><tr>{cols.map((c, i) => <th key={c} style={{ ...th, textAlign: right.includes(i) ? "right" : "left" }}>{c}</th>)}</tr></thead>
)
