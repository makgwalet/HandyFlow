// src/pages/projects/CreateProjectModal.tsx
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { X } from "lucide-react"

const inp: React.CSSProperties = { width:"100%", padding:"8px 11px", border:"1px solid #E2E8F0", borderRadius:8, fontSize:13, boxSizing:"border-box", outline:"none", background:"#fff", color:"#0F172A" }
const lbl: React.CSSProperties = { display:"block", fontSize:12, fontWeight:600, color:"#374151", marginBottom:4 }

export function CreateProjectModal({ onClose, onCreated }: { onClose:()=>void; onCreated:(id:string)=>void }) {
  const qc = useQueryClient()
  const [err, setErr] = useState("")
  const [form, setForm] = useState({
    name:"", projectType:"CONSTRUCTION", clientName:"", siteAddress:"",
    startDate:"", endDate:"", budgetTotal:"", contractValue:"",
    contractRef:"", projectManagerName:"", cidbGrade:"", nhbrcNumber:"", description:""
  })
  const sf = (k:string, v:string) => setForm(p => ({...p,[k]:v}))

  const mut = useMutation({
    mutationFn: (body:any) => apiClient.post("/api/v1/projects", body),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey:["pm-projects"] })
      qc.invalidateQueries({ queryKey:["pm-projects-active"] })
      qc.invalidateQueries({ queryKey:["pm-summary"] })
      const id = r.data?.data?.id ?? r.data?.id
      if (id) onCreated(id)
    },
    onError: (e:any) => setErr(e.response?.data?.message || "Failed to create project"),
  })

  return (
    <div style={{ position:"fixed", inset:0, background:"rgba(15,23,42,0.45)", display:"flex", alignItems:"center", justifyContent:"center", zIndex:1000 }}>
      <div style={{ background:"#fff", borderRadius:14, padding:28, width:620, maxHeight:"92vh", overflowY:"auto", boxShadow:"0 20px 60px rgba(0,0,0,0.18)" }}>
        <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
          <h3 style={{ margin:0, fontSize:16, fontWeight:700, color:"#0F172A" }}>New Project</h3>
          <button onClick={onClose} style={{ background:"none", border:"none", cursor:"pointer", color:"#94A3B8", padding:4 }}><X size={18}/></button>
        </div>
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
          <div style={{ gridColumn:"span 2" }}>
            <label style={lbl}>Project Name *</label>
            <input value={form.name} onChange={e=>sf("name",e.target.value)} placeholder="N2 Bridge Extension" style={inp} autoFocus/>
          </div>
          <div>
            <label style={lbl}>Type</label>
            <select value={form.projectType} onChange={e=>sf("projectType",e.target.value)} style={inp}>
              {["CONSTRUCTION","EARTHMOVING","SECURITY","EVENT","IT","GENERAL"].map(t=><option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div><label style={lbl}>Client</label><input value={form.clientName} onChange={e=>sf("clientName",e.target.value)} placeholder="Client name" style={inp}/></div>
          <div><label style={lbl}>Start Date</label><input type="date" value={form.startDate} onChange={e=>sf("startDate",e.target.value)} style={inp}/></div>
          <div><label style={lbl}>End Date</label><input type="date" value={form.endDate} onChange={e=>sf("endDate",e.target.value)} style={inp}/></div>
          <div><label style={lbl}>Budget (R) *</label><input type="number" value={form.budgetTotal} onChange={e=>sf("budgetTotal",e.target.value)} placeholder="0.00" style={inp}/></div>
          <div><label style={lbl}>Contract Value (R)</label><input type="number" value={form.contractValue} onChange={e=>sf("contractValue",e.target.value)} placeholder="0.00" style={inp}/></div>
          <div><label style={lbl}>Contract Ref</label><input value={form.contractRef} onChange={e=>sf("contractRef",e.target.value)} placeholder="JBCC-2026-001" style={inp}/></div>
          <div><label style={lbl}>Project Manager</label><input value={form.projectManagerName} onChange={e=>sf("projectManagerName",e.target.value)} placeholder="Full name" style={inp}/></div>
          <div><label style={lbl}>CIDB Grade</label><input value={form.cidbGrade} onChange={e=>sf("cidbGrade",e.target.value)} placeholder="7CE" style={inp}/></div>
          <div><label style={lbl}>NHBRC No.</label><input value={form.nhbrcNumber} onChange={e=>sf("nhbrcNumber",e.target.value)} placeholder="NHBRC-123456" style={inp}/></div>
          <div style={{ gridColumn:"span 2" }}><label style={lbl}>Site Address</label><input value={form.siteAddress} onChange={e=>sf("siteAddress",e.target.value)} placeholder="Erf 445 Halfway House, Midrand" style={inp}/></div>
          <div style={{ gridColumn:"span 2" }}><label style={lbl}>Description</label><textarea value={form.description} onChange={e=>sf("description",e.target.value)} style={{...inp,minHeight:56,resize:"vertical"}} placeholder="Brief scope…"/></div>
        </div>
        {err && <div style={{ marginTop:10, padding:"8px 12px", background:"#FEF2F2", border:"1px solid #FECACA", borderRadius:8, color:"#DC2626", fontSize:13 }}>{err}</div>}
        <div style={{ display:"flex", justifyContent:"flex-end", gap:10, marginTop:20 }}>
          <button onClick={onClose} style={{ padding:"8px 16px", border:"1px solid #E2E8F0", borderRadius:8, background:"#fff", fontSize:13, cursor:"pointer", color:"#64748B" }}>Cancel</button>
          <button disabled={mut.isPending} onClick={() => {
            if (!form.name.trim() || !form.budgetTotal) { setErr("Name and budget are required"); return }
            mut.mutate({ name:form.name.trim(), projectType:form.projectType, description:form.description||null, clientName:form.clientName||null, siteAddress:form.siteAddress||null, startDate:form.startDate||null, endDate:form.endDate||null, budgetTotal:parseFloat(form.budgetTotal), contractValue:form.contractValue?parseFloat(form.contractValue):null, contractRef:form.contractRef||null, projectManagerName:form.projectManagerName||null, cidbGrade:form.cidbGrade||null, nhbrcNumber:form.nhbrcNumber||null })
          }} style={{ padding:"8px 16px", background:"#1B3A6B", color:"#fff", border:"none", borderRadius:8, fontSize:13, fontWeight:600, cursor:"pointer", opacity:mut.isPending?.6:1 }}>
            {mut.isPending ? "Creating…" : "Create Project"}
          </button>
        </div>
      </div>
    </div>
  )
}
