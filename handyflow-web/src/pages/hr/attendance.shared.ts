// Styles, types and helpers shared by the attendance screens.
import type React from "react"

type ApiErr = { response?: { data?: { message?: string } } }
export const errorText = (e: unknown, fallback: string) => (e as ApiErr)?.response?.data?.message ?? fallback
export const unwrap = <T,>(r: { data?: { data?: unknown } }): T => (r.data?.data ?? r.data) as T

export const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", marginBottom: 20, overflow: "hidden" }
export const th: React.CSSProperties = { textAlign: "left", padding: "9px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }
export const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)", borderTop: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }
export const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }
export const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
export const primary: React.CSSProperties = { padding: "9px 18px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700, cursor: "pointer" }
export const plain: React.CSSProperties = { padding: "6px 12px", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer" }

export interface LogLine { action: string; oldIn: string | null; oldOut: string | null; newIn: string | null; newOut: string | null; reason: string; doneBy: string | null; doneAt: string }
export interface EntryView { id: string; clockIn: string; clockOut: string | null; source: string; corrected: boolean; log: LogLine[] }
export interface DayView {
  date: string; status: string; expectedMinutes: number; workedMinutes: number; lateMinutes: number; shortMinutes: number
  overtimeKind: string | null; overtimeMinutes: number; flags: string[]; entries: EntryView[]
  overtimeClaimId: string | null; overtimeClaimStatus: string | null
}
export interface Timesheet {
  employeeId: string; employeeNumber: string; employeeName: string; scheduleName: string; weeklyMinutes: number
  from: string; to: string; days: DayView[]; weekFlags: string[]
  workedMinutes: number; lateDays: number; absentDays: number; overtimeMinutes: number; canCorrect: boolean; aboveThreshold: boolean
}
