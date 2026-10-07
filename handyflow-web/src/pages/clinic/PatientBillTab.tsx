// src/pages/clinic/PatientBillTab.tsx
// Split out of PatientFilePage.tsx (W-4); behaviour unchanged.
import { useState } from "react"
import { CreditCard, Plus, X } from "lucide-react"
import type { BillLine, Patient } from "./patientFile.shared"
import { AMBER, BORDER, Empty, GRAY, GREEN_TEXT, LIGHT, Modal, ModalFooter, NAVY, NAVY_TEXT, RED_TEXT, TEAL, TEAL_TEXT, btnOutline, btnPrimary, fmtR, lbl, sinp } from "./patientFile.shared"

// ── RUNNING BILL TAB ──────────────────────────────────────────────────────────

export default function RunningBillTab({ billLines, onRemove, patient }:
  {billLines:BillLine[]; onRemove:(id:string)=>void; patient:Patient}) {
  const [showAdd, setShowAdd]   = useState(false)
  const [addType, setAddType]   = useState<"PROCEDURE"|"MEDICINE"|"CONSUMABLE">("PROCEDURE")
  const [addForm, setAddForm]   = useState({description:"",tariffCode:"",nappiCode:"",quantity:"1",unitPrice:""})
  const [extraLines, setExtra]  = useState<BillLine[]>([])

  const allLines=[...billLines,...extraLines]
  const total=allLines.reduce((s,l)=>s+l.gross,0)

  const addLine=()=>{
    if (!addForm.description||!addForm.unitPrice) return
    const qty=parseFloat(addForm.quantity)||1, price=parseFloat(addForm.unitPrice)||0
    setExtra(l=>[...l,{id:crypto.randomUUID(),type:addType,description:addForm.description,tariffCode:addForm.tariffCode||undefined,nappiCode:addForm.nappiCode||undefined,quantity:qty,unitPrice:price,gross:qty*price}])
    setAddForm({description:"",tariffCode:"",nappiCode:"",quantity:"1",unitPrice:""}); setShowAdd(false)
  }

  const TYPE_CFG:Record<string,{color:string;bg:string;label:string}> = {
    CONSULTATION:{color:TEAL_TEXT,bg:"var(--hf-success-soft)",label:"Consultation"},
    PROCEDURE:   {color:NAVY_TEXT,bg:"var(--hf-info-soft)",label:"Procedure"},
    MEDICINE:    {color:GREEN_TEXT,bg:"var(--hf-success-soft-strong)",label:"Medicine"},
    CONSUMABLE:  {color:AMBER,bg:"var(--hf-warning-soft)",label:"Consumable"},
  }

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div>
          <div style={{ fontSize:15, fontWeight:700, color:"var(--hf-text)" }}>Running bill</div>
          <div style={{ fontSize:12, color:GRAY, marginTop:2 }}>Items accumulate as the consultation progresses.</div>
        </div>
        <button onClick={()=>setShowAdd(true)} style={btnPrimary}><Plus size={14}/> Add item</button>
      </div>

      {allLines.length===0 ? (
        <Empty icon={CreditCard} msg="No items yet">
          <div style={{ fontSize:13, color:GRAY, marginTop:4 }}>Items are added when you record a consultation, or manually here.</div>
        </Empty>
      ) : (
        <>
          <div style={{ border:`1px solid ${BORDER}`, borderRadius:12, overflow:"hidden", marginBottom:16 }}>
            <table style={{ width:"100%", borderCollapse:"collapse" }}>
              <thead>
                <tr style={{ background:LIGHT, borderBottom:`1px solid ${BORDER}` }}>
                  {["Type","Description","Code","Qty","Unit price","Total",""].map(h=>(
                    <th key={h} style={{ padding:"10px 14px", textAlign:"left", fontSize:11, fontWeight:700, color:GRAY, letterSpacing:"0.04em" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {allLines.map((l,i)=>{
                  const cfg=TYPE_CFG[l.type]??TYPE_CFG.CONSULTATION
                  const isExtra=!billLines.find(b=>b.id===l.id)
                  return (
                    <tr key={l.id} style={{ borderBottom:i<allLines.length-1?`1px solid var(--hf-border-subtle)`:"none" }}>
                      <td style={{ padding:"11px 14px" }}><span style={{ background:cfg.bg, color:cfg.color, padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>{cfg.label}</span></td>
                      <td style={{ padding:"11px 14px", fontSize:13, fontWeight:600, color:"var(--hf-text)" }}>{l.description}</td>
                      <td style={{ padding:"11px 14px", fontSize:12, color:GRAY }}>{l.tariffCode||l.nappiCode||"—"}</td>
                      <td style={{ padding:"11px 14px", fontSize:13, color:"var(--hf-text)" }}>{l.quantity}</td>
                      <td style={{ padding:"11px 14px", fontSize:13, color:"var(--hf-text)" }}>{fmtR(l.unitPrice)}</td>
                      <td style={{ padding:"11px 14px", fontSize:13, fontWeight:700, color:"var(--hf-text)" }}>{fmtR(l.gross)}</td>
                      <td style={{ padding:"11px 14px" }}><button onClick={()=>isExtra?setExtra(e=>e.filter(x=>x.id!==l.id)):onRemove(l.id)} style={{ background:"none", border:"none", cursor:"pointer", color:RED_TEXT, display:"flex" }}><X size={14}/></button></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-end" }}>
            <div style={{ display:"flex", gap:10 }}>
              <button style={{ ...btnOutline, color:TEAL_TEXT, borderColor:TEAL }}>Generate claim (medical aid)</button>
              <button style={{ ...btnOutline, color:NAVY_TEXT, borderColor:NAVY }}>Record payment</button>
            </div>
            <div style={{ padding:"16px 24px", background:NAVY, borderRadius:12, textAlign:"right" as const }}>
              <div style={{ fontSize:11, fontWeight:700, color:"rgba(255,255,255,0.6)", textTransform:"uppercase", letterSpacing:"0.06em" }}>Total</div>
              <div style={{ fontSize:26, fontWeight:800, color:"var(--hf-text-on-solid)" }}>{fmtR(total)}</div>
              <div style={{ fontSize:10, color:"rgba(255,255,255,0.4)" }}>excl. VAT</div>
            </div>
          </div>
        </>
      )}

      {showAdd&&(
        <Modal title="Add billing item" onClose={()=>setShowAdd(false)}>
          <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
            <div>
              <label style={lbl}>Item type</label>
              <div style={{ display:"flex", gap:6 }}>
                {(["PROCEDURE","MEDICINE","CONSUMABLE"] as const).map(t=>(
                  <button key={t} onClick={()=>setAddType(t)} style={{ padding:"6px 14px", borderRadius:8, border:`2px solid ${addType===t?NAVY:BORDER}`, background:addType===t?"var(--hf-info-soft)":"var(--hf-surface)", color:addType===t?NAVY_TEXT:GRAY, fontSize:12, fontWeight:addType===t?600:400, cursor:"pointer" }}>
                    {t.charAt(0)+t.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
            </div>
            <div><label style={lbl}>Description *</label><input value={addForm.description} onChange={e=>setAddForm(f=>({...f,description:e.target.value}))} placeholder="e.g. Wound suture — simple" style={sinp} autoFocus/></div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
              {addType==="PROCEDURE"&&<div><label style={lbl}>Tariff code</label><input value={addForm.tariffCode} onChange={e=>setAddForm(f=>({...f,tariffCode:e.target.value}))} placeholder="0007" style={sinp}/></div>}
              {addType==="MEDICINE"&&<div><label style={lbl}>NAPPI code</label><input value={addForm.nappiCode} onChange={e=>setAddForm(f=>({...f,nappiCode:e.target.value}))} placeholder="701408001" style={sinp}/></div>}
              <div><label style={lbl}>Quantity</label><input type="number" step="0.5" value={addForm.quantity} onChange={e=>setAddForm(f=>({...f,quantity:e.target.value}))} style={sinp}/></div>
              <div><label style={lbl}>Unit price (R) *</label><input type="number" step="0.01" value={addForm.unitPrice} onChange={e=>setAddForm(f=>({...f,unitPrice:e.target.value}))} placeholder="0.00" style={sinp}/></div>
            </div>
            {addForm.quantity&&addForm.unitPrice&&<div style={{ padding:"8px 12px", background:"var(--hf-success-soft)", border:"1px solid var(--hf-success-border)", borderRadius:8, fontSize:13, color:GREEN_TEXT, fontWeight:600 }}>Line total: {fmtR(parseFloat(addForm.quantity)*parseFloat(addForm.unitPrice))}</div>}
          </div>
          <ModalFooter onCancel={()=>setShowAdd(false)} onConfirm={addLine} confirmLabel="Add to bill"/>
        </Modal>
      )}
    </div>
  )
}

