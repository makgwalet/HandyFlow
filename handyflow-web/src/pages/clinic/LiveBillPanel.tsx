// src/pages/clinic/LiveBillPanel.tsx
// The running bill of the live consultation session: the lines, "Add custom item" and the total.
// Moved out of ConsultationSession.tsx; a custom item now needs a description, a quantity above
// zero and a price of zero or more (before, an empty price added a line worth NaN).

import { useState } from "react"
import { Plus, X } from "lucide-react"
import {
  AMBER, BORDER, GRAY, GRAY_TEXT, GREEN, LIGHT, NAVY, RED_TEXT, TEAL,
  cancelBtn, fmtR, primaryBtn, sinp, type BillLine,
} from "./consultationSession.shared"

export interface CustomLineForm { description: string; quantity: string; unitPrice: string }
export const BLANK_CUSTOM: CustomLineForm = { description: "", quantity: "1", unitPrice: "" }

/** The bill line a custom-item form makes, or the reason it cannot be added. */
export function customBillLine(f: CustomLineForm): { line: Omit<BillLine, "id"> } | { error: string } {
  const description = f.description.trim()
  if (!description) return { error: "Describe the item" }
  const qty = parseFloat(f.quantity === "" ? "1" : f.quantity)
  if (!(qty > 0)) return { error: "Quantity must be more than zero" }
  const price = parseFloat(f.unitPrice === "" ? "0" : f.unitPrice)
  if (!(price >= 0)) return { error: "Price cannot be negative" }
  return { line: { type: "CONSUMABLE", description, quantity: qty, unitPrice: price, gross: Math.round(qty * price * 100) / 100 } }
}

interface Props {
  billLines: BillLine[]
  billTotal: number
  removeBillLine: (id: string) => void
  addBillLine: (line: Omit<BillLine, "id">) => void
}

export default function LiveBillPanel({ billLines, billTotal, removeBillLine, addBillLine }: Props) {
  const [showCustom, setShowCustom] = useState(false)
  const [customLine, setCustomLine] = useState<CustomLineForm>(BLANK_CUSTOM)
  const [error, setError] = useState("")
  return (
    <>
          {/* Bill lines */}
          <div style={{ flex:1, overflowY:"auto", display:"flex", flexDirection:"column", gap:6 }}>
            {billLines.map((line)=>{
              const typeColor:Record<string,string> = {
                CONSULTATION:TEAL, PROCEDURE:NAVY, MEDICINE:GREEN, CONSUMABLE:AMBER
              }
              const col = typeColor[line.type]??GRAY
              return (
                <div key={line.id} style={{ display:"flex", alignItems:"center", gap:8,
                  padding:"8px 12px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`,
                  borderLeft:`3px solid ${col}`, borderRadius:8 }}>
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{ fontSize:12, fontWeight:600, color:"var(--hf-text)",
                      overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" as const }}>
                      {line.description}
                    </div>
                    <div style={{ fontSize:10, color:GRAY_TEXT }}>
                      {line.tariffCode||line.nappiCode||""} · qty {line.quantity}
                    </div>
                  </div>
                  <div style={{ fontSize:13, fontWeight:700, color:"var(--hf-text)", flexShrink:0 }}>
                    {fmtR(line.gross)}
                  </div>
                  {line.id!=="consult-0191" && (
                    <button onClick={()=>removeBillLine(line.id)}
                      style={{background:"none",border:"none",cursor:"pointer",color:RED_TEXT,display:"flex",padding:2}}>
                      <X size={12}/>
                    </button>
                  )}
                </div>
              )
            })}

            {/* Add custom line */}
            {!showCustom ? (
              <button onClick={()=>setShowCustom(true)}
                style={{display:"flex",alignItems:"center",gap:5,padding:"7px 12px",
                  border:`1px dashed ${BORDER}`,borderRadius:8,background:LIGHT,
                  color:GRAY_TEXT,fontSize:12,cursor:"pointer"}}>
                <Plus size={12}/> Add custom item
              </button>
            ) : (
              <div style={{padding:"10px 12px",background:LIGHT,border:`1px solid ${BORDER}`,borderRadius:8}}>
                <div style={{display:"grid",gridTemplateColumns:"2fr 1fr 1fr",gap:8,marginBottom:8}}>
                  <input value={customLine.description} onChange={e=>{setCustomLine(f=>({...f,description:e.target.value})); setError("")}}
                    placeholder="Description" style={{...sinp,padding:"6px 8px",fontSize:12}} autoFocus/>
                  <input type="number" value={customLine.quantity} onChange={e=>{setCustomLine(f=>({...f,quantity:e.target.value})); setError("")}}
                    placeholder="Qty" style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                  <input type="number" step="0.01" value={customLine.unitPrice} onChange={e=>{setCustomLine(f=>({...f,unitPrice:e.target.value})); setError("")}}
                    placeholder="R price" style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
                {error && <div role="alert" style={{fontSize:12,color:RED_TEXT,marginBottom:6}}>{error}</div>}
                <div style={{display:"flex",gap:6,justifyContent:"flex-end"}}>
                  <button onClick={()=>{setShowCustom(false); setError("")}} style={{...cancelBtn,padding:"4px 10px",fontSize:11}}>Cancel</button>
                  <button onClick={()=>{
                    const made = customBillLine(customLine)
                    if ("error" in made) { setError(made.error); return }
                    addBillLine(made.line)
                    setCustomLine(BLANK_CUSTOM); setError(""); setShowCustom(false)
                  }} style={{...primaryBtn,padding:"4px 10px",fontSize:11}}>Add</button>
                </div>
              </div>
            )}
          </div>

          {/* Bill total */}
          <div style={{padding:"12px 16px",background:NAVY,borderRadius:10,display:"flex",justifyContent:"space-between",alignItems:"center"}}>
            <span style={{fontSize:12,color:"rgba(255,255,255,0.6)"}}>Total · {billLines.length} items</span>
            <span style={{fontSize:20,fontWeight:800,color:"var(--hf-text-on-solid)"}}>{fmtR(billTotal)}</span>
          </div>
    </>
  )
}
