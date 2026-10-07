// src/pages/clinic/consultationSession.shared.tsx
// Types, design tokens and small helpers used by the live consultation session.
// Moved out of ConsultationSession.tsx unchanged (apart from `export`).

import { Syringe, FlaskConical, Scissors, Zap } from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

export interface Patient { id: string; fullName: string; bloodType?: string; allergies?: string[] }
export interface Appointment {
  id: string; patientId: string; patientName: string
  practitionerId: string; practitionerName: string
  appointmentType: string; reason: string; scheduledAt: string
}
export interface BillLine {
  id: string; type: "CONSULTATION"|"PROCEDURE"|"MEDICINE"|"CONSUMABLE"
  description: string; tariffCode?: string; nappiCode?: string
  quantity: number; unitPrice: number; gross: number
}
export interface RxDraft {
  id: string; medicationName: string; nappiCode?: string
  dosage: string; frequency: string; duration: string
  quantity: number; instructions: string; fromBill: boolean
  allergyReason?: string
}

// ── Tokens ────────────────────────────────────────────────────────────────────

export const NAVY="var(--hf-primary)";
export const NAVY_TEXT = "var(--hf-primary-text)"
export const TEAL ="var(--hf-accent)";
export const TEAL_TEXT = "var(--hf-accent-text)"
export const RED ="var(--hf-danger)"
export const RED_TEXT = "var(--hf-danger-text)";
export const GREEN="var(--hf-success-solid-strong)";
export const GREEN_TEXT = "var(--hf-success-text-strong)"
export const AMBER ="var(--hf-warning)";
export const AMBER_TEXT = "var(--hf-warning-text)"
export const PURPLE ="var(--hf-violet)"
export const PURPLE_TEXT = "var(--hf-violet-text)";
export const GRAY="var(--hf-neutral-solid)";
export const GRAY_TEXT = "var(--hf-text-muted)"
export const BORDER ="var(--hf-border)"
export const LIGHT ="var(--hf-surface-muted)"

export const QUICK_PROCEDURES = [
  { label:"Injection IM",    tariff:"0115", price: 85,  icon: Syringe,      type:"PROCEDURE" },
  { label:"Injection IV",    tariff:"0116", price:120,  icon: Syringe,      type:"PROCEDURE" },
  { label:"Blood draw",      tariff:"0301", price: 95,  icon: FlaskConical, type:"PROCEDURE" },
  { label:"Wound suture",    tariff:"0007", price:180,  icon: Scissors,     type:"PROCEDURE" },
  { label:"ECG 12-lead",     tariff:"4116", price:350,  icon: Zap,          type:"PROCEDURE" },
]

export const fmtR = (v: number) => `R ${(v||0).toLocaleString("en-ZA",{minimumFractionDigits:2})}`

export function padZero(n: number) { return String(n).padStart(2,"0") }
export function fmtTimer(seconds: number) {
  const h = Math.floor(seconds/3600)
  const m = Math.floor((seconds%3600)/60)
  const s = seconds%60
  return h > 0 ? `${padZero(h)}:${padZero(m)}:${padZero(s)}` : `${padZero(m)}:${padZero(s)}`
}

export const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }


export const lbl:React.CSSProperties         = {display:"block",fontSize:11,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:3}
export const sectionLabel:React.CSSProperties = {fontSize:10,fontWeight:700,color:GRAY_TEXT,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:8}
export const sinp:React.CSSProperties        = {width:"100%",padding:"8px 10px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:7,fontSize:13,outline:"none",background:"var(--hf-surface)"}
export const primaryBtn:React.CSSProperties  = {background:NAVY,color:"var(--hf-text-on-solid)",border:"none",borderRadius:9,padding:"9px 18px",fontSize:13,fontWeight:600,cursor:"pointer"}
export const cancelBtn:React.CSSProperties   = {padding:"9px 16px",border:`1px solid ${BORDER}`,borderRadius:9,background:"var(--hf-surface)",fontSize:13,cursor:"pointer",color:"var(--hf-text-secondary)"}
export const voiceBtn = (bg:string):React.CSSProperties => ({display:"flex",alignItems:"center",gap:4,padding:"4px 10px",background:bg,color:"var(--hf-text-on-solid)",border:"none",borderRadius:6,fontSize:11,fontWeight:600,cursor:"pointer"})
