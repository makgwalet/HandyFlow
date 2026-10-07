// src/pages/clinic/patientFile.shared.tsx
// Types, design tokens, formatters and small layout helpers shared by the patient file and its tabs.
import ModalShell from "./ModalShell"
import { apiClient } from "../../api/client"
import { User, Calendar, AlertCircle, CheckCircle, PlayCircle, XCircle, Loader } from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

export interface Patient {
  id: string; firstName: string; lastName: string; fullName: string
  idNumber: string; dateOfBirth: string; gender: string
  phone: string; email: string; bloodType: string
  allergies: string[]; chronicConditions: string[]
  emergencyContactName: string; emergencyContactPhone: string
  notes: string; active: boolean
  accountType: "INDIVIDUAL" | "PRINCIPAL" | "DEPENDANT"
  principalId?: string; principalName?: string
  relationship?: string; lastVisitAt?: string; archivedAt?: string
}
export interface Appointment {
  id: string; patientName: string; practitionerName: string
  scheduledAt: string; durationMinutes: number
  appointmentType: string; status: string; reason: string
}
export interface Consultation {
  id: string; practitionerName: string; consultedAt: string
  weightKg: number; heightCm: number; bloodPressure: string
  pulseBpm: number; temperatureC: number; oxygenSatPct: number
  chiefComplaint: string; history: string; examination: string
  diagnosis: string; icd10Codes: string[]; treatmentPlan: string
  followUpDays: number | null; billed: boolean; billingAmount: number
}
export interface Prescription {
  id: string; medicationName: string; dosage: string; frequency: string
  duration: string; quantity: number; repeats: number; instructions: string
  dispensed: boolean; prescribedAt: string; practitionerName?: string
  fillsUsed?: number; fillsRemaining?: number
}
export interface Practitioner { id: string; fullName: string; specialty: string }
export interface BillLine {
  id: string; type: "CONSULTATION"|"PROCEDURE"|"MEDICINE"|"CONSUMABLE"
  description: string; tariffCode?: string; nappiCode?: string
  quantity: number; unitPrice: number; gross: number
}

// ── Design tokens ─────────────────────────────────────────────────────────────

export const NAVY="var(--hf-primary)";
export const NAVY_TEXT = "var(--hf-primary-text)";
export const TEAL="var(--hf-accent)";
export const TEAL_TEXT = "var(--hf-accent-text)";
export const RED="var(--hf-danger)"
export const RED_TEXT = "var(--hf-danger-text)";
export const GREEN="var(--hf-success-solid-strong)";
export const GREEN_TEXT = "var(--hf-success-text-strong)";
export const AMBER="var(--hf-warning-text)";
export const PURPLE="var(--hf-violet)"
export const PURPLE_TEXT = "var(--hf-violet-text)";
export const GRAY="var(--hf-text-muted)";
export const BORDER="var(--hf-border)";
export const LIGHT="var(--hf-surface-muted)"

export const STATUS_CFG: Record<string,{color:string;bg:string;label:string;icon:any}> = {
  SCHEDULED:   {color:"var(--hf-info-text)",bg:"var(--hf-info-soft)",label:"Scheduled",  icon:Calendar},
  CONFIRMED:   {color:PURPLE_TEXT,   bg:"var(--hf-violet-soft)",label:"Confirmed",  icon:CheckCircle},
  CHECKED_IN:  {color:"var(--hf-info-text)",bg:"var(--hf-info-soft)",label:"Checked in", icon:CheckCircle},
  TRIAGED:     {color:PURPLE_TEXT,   bg:"var(--hf-violet-soft)",label:"Triaged",    icon:CheckCircle},
  IN_PROGRESS: {color:AMBER,    bg:"var(--hf-warning-soft)",label:"In Progress",icon:PlayCircle},
  COMPLETED:   {color:GREEN_TEXT,    bg:"var(--hf-success-soft-strong)",label:"Completed",  icon:CheckCircle},
  CANCELLED:   {color:RED_TEXT,      bg:"var(--hf-danger-soft)",label:"Cancelled",  icon:XCircle},
  NO_SHOW:     {color:GRAY,     bg:LIGHT,    label:"No Show",    icon:User},
}
export const STATUS_FLOW: Record<string,{action:string;label:string;color:string}[]> = {
  SCHEDULED:   [{action:"check_in",label:"Check in",color:"var(--hf-info-text)"},{action:"confirm",label:"Confirm",color:PURPLE_TEXT},{action:"cancel",label:"Cancel",color:RED_TEXT}],
  CONFIRMED:   [{action:"check_in",label:"Check in",color:"var(--hf-info-text)"},{action:"start",label:"Start",color:AMBER},{action:"no_show",label:"No Show",color:GRAY},{action:"cancel",label:"Cancel",color:RED_TEXT}],
  CHECKED_IN:  [{action:"triage",label:"Triage",color:"var(--hf-violet-text)"},{action:"start",label:"Start",color:"var(--hf-warning-text)"},{action:"cancel",label:"Cancel",color:"var(--hf-danger-text)"}],
  TRIAGED:     [{action:"start",label:"Start",color:"var(--hf-warning-text)"},{action:"cancel",label:"Cancel",color:"var(--hf-danger-text)"}],
  IN_PROGRESS: [{action:"complete",label:"Complete",color:GREEN_TEXT}],
}
export const ACCOUNT_CFG: Record<string,{label:string;bg:string;color:string}> = {
  INDIVIDUAL: {label:"Individual",bg:"var(--hf-info-soft)",color:"var(--hf-info-text)"},
  PRINCIPAL:  {label:"Principal", bg:"var(--hf-success-soft)",color:"var(--hf-success-text-strong)"},
  DEPENDANT:  {label:"Dependant", bg:"var(--hf-violet-soft)",color:PURPLE_TEXT},
}

export const saId = (id?: string) => {
  const c=(id??"").replace(/\D/g,""); if (c.length!==13) return null
  const yy=+c.slice(0,2),mm=+c.slice(2,4),dd=+c.slice(4,6)
  const yr=yy<=(new Date().getFullYear()%100)?2000+yy:1900+yy
  const months=["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
  return { dob:`${String(dd).padStart(2,"0")} ${months[mm-1]} ${yr}`,
    age:Math.floor((Date.now()-new Date(yr,mm-1,dd).getTime())/(365.25*24*3600*1000)),
    gender:+c[6]>=5?"Male":"Female" }
}
export const fmtDT  = (iso:string) => new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric"})
export const fmtTime= (iso:string) => new Date(iso).toLocaleTimeString("en-ZA",{hour:"2-digit",minute:"2-digit"})
export const fmtR   = (v:number)   => `R ${(v??0).toLocaleString("en-ZA",{minimumFractionDigits:2})}`
export const unwrap = (r:any)      => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

export const downloadPdf = async (url:string, filename:string) => {
  const res = await apiClient.get(url,{responseType:"blob"})
  const link = document.createElement("a")
  link.href = URL.createObjectURL(new Blob([res.data],{type:"application/pdf"}))
  link.download = filename; link.click(); URL.revokeObjectURL(link.href)
}

// ── Shared helpers ─────────────────────────────────────────────────────────────

export function Modal({ title, onClose, children, wide }:{title:string;onClose:()=>void;children:React.ReactNode;wide?:boolean}) {
  return <ModalShell title={title} onClose={onClose} width={wide?740:500}>{children}</ModalShell>
}
export function ModalFooter({ onCancel, onConfirm, confirmLabel, loading }:{onCancel:()=>void;onConfirm:()=>void;confirmLabel:string;loading?:boolean}) {
  return <div style={{ display:"flex", gap:10, justifyContent:"flex-end", marginTop:20 }}><button onClick={onCancel} style={btnCancel}>Cancel</button><button onClick={onConfirm} disabled={loading} style={btnPrimary}>{loading?<><Loader size={13}/> {confirmLabel}</>:confirmLabel}</button></div>
}
export function Empty({ icon:Icon, msg, children }:{icon:React.ElementType;msg:string;children?:React.ReactNode}) {
  return <div style={{ textAlign:"center", padding:"60px 20px", color:GRAY, border:`1px dashed ${BORDER}`, borderRadius:12 }}><Icon size={36} style={{ marginBottom:12, opacity:0.4 }}/><div style={{ fontWeight:600, color:"var(--hf-text-tertiary)", fontSize:15 }}>{msg}</div>{children}</div>
}
export function ErrBox({ msg }:{msg:string}) {
  return <div style={{ padding:"10px 12px", background:"var(--hf-danger-soft)", border:"1px solid var(--hf-danger-border)", borderRadius:8, fontSize:13, color:RED_TEXT, display:"flex", alignItems:"center", gap:8 }}><AlertCircle size={14}/>{msg}</div>
}
export function FSect({ title, children }:{title:string;children:React.ReactNode}) {
  return <div style={{ marginBottom:20 }}><div style={{ fontSize:10, fontWeight:700, color:GRAY, letterSpacing:"0.07em", textTransform:"uppercase", marginBottom:12, paddingBottom:8, borderBottom:`1px solid ${BORDER}` }}>{title}</div>{children}</div>
}

export const lbl:React.CSSProperties     = {display:"block",fontSize:13,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}
export const sinp:React.CSSProperties    = {width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:8,fontSize:14,outline:"none",background:"var(--hf-surface)"}
export const btnPrimary:React.CSSProperties = {display:"flex",alignItems:"center",gap:7,background:NAVY,color:"var(--hf-text-on-solid)",border:"none",borderRadius:9,padding:"9px 20px",fontSize:13,fontWeight:600,cursor:"pointer"}
export const btnCancel:React.CSSProperties  = {padding:"9px 18px",border:`1px solid ${BORDER}`,borderRadius:9,background:"var(--hf-surface)",fontSize:13,cursor:"pointer",color:"var(--hf-text-secondary)"}
export const btnOutline:React.CSSProperties = {padding:"9px 18px",border:"1.5px solid",borderRadius:9,background:"var(--hf-surface)",fontSize:13,fontWeight:600,cursor:"pointer"}

