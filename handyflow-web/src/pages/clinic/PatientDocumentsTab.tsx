// src/pages/clinic/PatientDocumentsTab.tsx
// Split out of PatientFilePage.tsx (W-4); behaviour unchanged.
import { useState } from "react"
import { Pill, FileText, Download } from "lucide-react"
import type { Consultation, Patient } from "./patientFile.shared"
import { BORDER, GRAY, LIGHT, Modal, ModalFooter, NAVY_TEXT, PURPLE_TEXT, TEAL_TEXT, downloadPdf, fmtDT, lbl, sinp } from "./patientFile.shared"

// ── DOCUMENTS TAB ─────────────────────────────────────────────────────────────

export default function DocumentsTab({ patient, consultations }:
  {patient:Patient; consultations:Consultation[]}) {
  const [showCert, setShowCert]=useState(false)
  const [certForm, setCertForm]=useState({consultationId:"",unfitFrom:"",unfitTo:"",notes:""})
  const [loading, setLoading]=useState(false)
  const docs=[
    {icon:FileText, label:"Medical certificate", desc:"Generate sick note — select consultation + dates", color:PURPLE_TEXT, action:()=>setShowCert(true)},
    {icon:Pill,     label:"Prescription PDF",    desc:"Download prescription from latest consultation",  color:TEAL_TEXT,   action:()=>{ if (consultations[0]) downloadPdf(`/api/v1/clinic/consultations/${consultations[0].id}/prescription-pdf`,`rx-${patient.id}.pdf`) }},
    {icon:Download, label:"Patient summary",     desc:"Full record export for referral or transfer",     color:NAVY_TEXT,   action:()=>{}},
  ]
  return (
    <div>
      <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:14, marginBottom:24 }}>
        {docs.map(d=>(
          <button key={d.label} onClick={d.action} style={{ display:"flex", alignItems:"flex-start", gap:14, padding:"18px 20px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:12, cursor:"pointer", textAlign:"left" as const }}
            onMouseEnter={e=>{ (e.currentTarget as HTMLButtonElement).style.borderColor=d.color; (e.currentTarget as HTMLButtonElement).style.background=LIGHT }}
            onMouseLeave={e=>{ (e.currentTarget as HTMLButtonElement).style.borderColor=BORDER; (e.currentTarget as HTMLButtonElement).style.background="var(--hf-surface)" }}>
            <div style={{ width:40, height:40, borderRadius:10, background:`color-mix(in srgb, ${d.color} 8%, transparent)`, display:"flex", alignItems:"center", justifyContent:"center", flexShrink:0 }}><d.icon size={18} style={{ color: d.color }}/></div>
            <div><div style={{ fontSize:14, fontWeight:700, color:"var(--hf-text)", marginBottom:3 }}>{d.label}</div><div style={{ fontSize:12, color:GRAY }}>{d.desc}</div></div>
          </button>
        ))}
      </div>
      {showCert&&(
        <Modal title="Medical certificate" onClose={()=>setShowCert(false)}>
          <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
            <div><label style={lbl}>Consultation *</label><select value={certForm.consultationId} onChange={e=>setCertForm(f=>({...f,consultationId:e.target.value}))} style={sinp}><option value="">Select consultation...</option>{consultations.map(c=><option key={c.id} value={c.id}>{fmtDT(c.consultedAt)} — {c.chiefComplaint}</option>)}</select></div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
              <div><label style={lbl}>Unfit from</label><input type="date" value={certForm.unfitFrom} onChange={e=>setCertForm(f=>({...f,unfitFrom:e.target.value}))} style={sinp}/></div>
              <div><label style={lbl}>Unfit until</label><input type="date" value={certForm.unfitTo} onChange={e=>setCertForm(f=>({...f,unfitTo:e.target.value}))} style={sinp}/></div>
            </div>
            <div><label style={lbl}>Notes</label><textarea rows={2} value={certForm.notes} onChange={e=>setCertForm(f=>({...f,notes:e.target.value}))} placeholder="Additional notes..." style={{ ...sinp, resize:"vertical" as const }}/></div>
          </div>
          <ModalFooter onCancel={()=>setShowCert(false)}
            onConfirm={async()=>{ if (!certForm.consultationId) return; setLoading(true); try { const p=new URLSearchParams(); if(certForm.unfitFrom)p.set("unfitFrom",certForm.unfitFrom); if(certForm.unfitTo)p.set("unfitTo",certForm.unfitTo); if(certForm.notes)p.set("notes",certForm.notes); await downloadPdf(`/api/v1/clinic/consultations/${certForm.consultationId}/medical-certificate?${p}`,`med-cert-${patient.id}.pdf`); setShowCert(false) } finally { setLoading(false) } }}
            confirmLabel={loading?"Generating...":"Download certificate"} loading={loading}/>
        </Modal>
      )}
    </div>
  )
}

