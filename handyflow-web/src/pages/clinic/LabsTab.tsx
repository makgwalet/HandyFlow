// src/pages/clinic/LabsTabEnhanced.tsx
// Drop-in replacement for the LabsTab function inside PatientFilePage.tsx
// Features: upload, AI interpretation via Claude, marker display, filing to consultation
// Copy this entire function and replace the existing LabsTab in PatientFilePage.tsx

import { useState, useRef } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  FlaskConical, Upload, Download, Eye, CheckCircle, AlertCircle,
  Loader, ChevronDown, ChevronUp, Plus, X, FileText, Sparkles,
} from "lucide-react"

interface LabResult {
  id: string; source: string; labReference?: string
  receivedAt: string; pdfFilename?: string; pdfUrl?: string
  status: "UNREVIEWED" | "REVIEWED" | "FILED" | "REJECTED"
  patientNameRaw?: string; parsedMarkersJson?: string
  interpretation?: string; reviewedAt?: string; reviewedBy?: string
  collectedAt?: string; consultationId?: string
}
interface Marker {
  marker: string; value: string; unit?: string
  refRange?: string; flag?: "HIGH" | "LOW" | "NORMAL"
}
interface Consultation { id: string; chiefComplaint: string; consultedAt: string }

const TEAL="#0D9488"; const RED="#DC2626"; const GREEN="#166534"
const AMBER="#D97706"; const GRAY="#64748B"; const NAVY="#1B3A6B"
const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const fmtDT = (iso?: string) => iso
  ? new Date(iso).toLocaleDateString("en-ZA", { day:"numeric", month:"short", year:"numeric" })
  : "—"

const STATUS_LAB: Record<string,{color:string;bg:string;border:string}> = {
  UNREVIEWED: {color:RED,   bg:"#FEF2F2",border:"#FECACA"},
  REVIEWED:   {color:AMBER, bg:"#FFFBEB",border:"#FDE68A"},
  FILED:      {color:GREEN, bg:"#DCFCE7",border:"#86EFAC"},
  REJECTED:   {color:GRAY,  bg:LIGHT,    border:BORDER},
}

const FLAG_CFG = {
  HIGH:   {color:RED,   bg:"#FEF2F2",label:"H"},
  LOW:    {color:AMBER, bg:"#FFFBEB",label:"L"},
  NORMAL: {color:GREEN, bg:"#DCFCE7",label:"N"},
}

const parseMarkers = (json?: string): Marker[] => {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

// ── Main Labs Tab ─────────────────────────────────────────────────────────────

interface LabsTabProps { patient: { id: string; fullName: string } }

export function LabsTabEnhanced({ patient }: LabsTabProps) {
  const qc = useQueryClient()
  const [showUpload, setShowUpload]   = useState(false)
  const [expanded, setExpanded]       = useState<string|null>(null)
  const [interpreting, setInterpreting] = useState<string|null>(null)
  const [showFile, setShowFile]       = useState<string|null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const [uploadForm, setUploadForm]   = useState({
    source:"MANUAL", labReference:"", pdfFilename:"", collectedAt:"", notes:""
  })
  const [uploading, setUploading]     = useState(false)
  const [uploadError, setUploadError] = useState("")

  const { data: labs=[], isLoading } = useQuery<LabResult[]>({
    queryKey: ["pf-labs", patient.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/lab/patients/${patient.id}/results`)),
  })

  const { data: consultations=[] } = useQuery<Consultation[]>({
    queryKey: ["pf-consultations-short", patient.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patient.id}/consultations`)),
    enabled: showFile !== null,
  })

  const markReviewed = useMutation({
    mutationFn: (id:string) => apiClient.post(`/api/v1/clinic/lab/results/${id}/review`, null, {params:{reviewedBy:"current"}}),
    onSuccess: () => qc.invalidateQueries({queryKey:["pf-labs",patient.id]}),
  })

  const fileResult = useMutation({
    mutationFn: ({id,consultationId}:{id:string;consultationId:string}) =>
      apiClient.post(`/api/v1/clinic/lab/results/${id}/file`, null, {params:{consultationId}}),
    onSuccess: () => { qc.invalidateQueries({queryKey:["pf-labs",patient.id]}); setShowFile(null) },
  })

  const saveInterpretation = useMutation({
    mutationFn: ({id,text}:{id:string;text:string}) =>
      apiClient.post(`/api/v1/clinic/lab/results/${id}/interpret`, null, {params:{interpretation:text}}),
    onSuccess: () => qc.invalidateQueries({queryKey:["pf-labs",patient.id]}),
  })

  // ── Claude interpretation ─────────────────────────────────────────────────

  const interpretWithClaude = async (lab: LabResult) => {
    setInterpreting(lab.id)
    try {
      const markers = parseMarkers(lab.parsedMarkersJson)
      const markerText = markers.length > 0
        ? markers.map(m => `${m.marker}: ${m.value}${m.unit?` ${m.unit}`:""} (ref: ${m.refRange||"N/A"}) ${m.flag||""}`).join("\n")
        : `Lab result file: ${lab.pdfFilename || "uploaded PDF"}, Source: ${lab.source}`

      const res = await fetch("https://api.anthropic.com/v1/messages", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "claude-sonnet-4-6",
          max_tokens: 1000,
          messages: [{
            role: "user",
            content: `You are a clinical assistant helping a South African doctor understand lab results. 
Write a clear, plain-language interpretation of these lab results for the doctor's review. 
Highlight any abnormal values and their clinical significance. Be concise (3-5 sentences max).
Do NOT give treatment recommendations — just interpret the findings.

Patient: ${patient.fullName}
Lab source: ${lab.source}
${lab.collectedAt ? `Collected: ${fmtDT(lab.collectedAt)}` : ""}

Results:
${markerText}`
          }]
        })
      })
      const data = await res.json()
      const text = data.content?.[0]?.text ?? ""
      if (text) {
        await saveInterpretation.mutateAsync({id:lab.id, text})
      }
    } catch(e) { console.error("Interpretation failed", e) }
    setInterpreting(null)
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
        <div>
          <div style={{fontSize:15,fontWeight:700,color:"#0F172A"}}>Lab results</div>
          <div style={{fontSize:12,color:GRAY,marginTop:2}}>
            {(labs as LabResult[]).length} result{(labs as LabResult[]).length!==1?"s":""} on file
          </div>
        </div>
        <button onClick={()=>{setShowUpload(true);setUploadError("")}}
          style={{display:"flex",alignItems:"center",gap:6,background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 16px",fontSize:13,fontWeight:600,cursor:"pointer"}}>
          <Upload size={14}/> Upload result
        </button>
      </div>

      {isLoading ? <Spinner/>
      : (labs as LabResult[]).length===0 ? (
        <div style={{textAlign:"center",padding:"60px 20px",color:GRAY,border:`1px dashed ${BORDER}`,borderRadius:12}}>
          <FlaskConical size={36} style={{marginBottom:12,opacity:0.4}}/>
          <div style={{fontWeight:600,color:"#475569",fontSize:15}}>No lab results on file</div>
          <div style={{fontSize:13,marginTop:4}}>Upload results from Ampath, Lancet, Pathcare or any lab to attach them to this patient record.</div>
        </div>
      ) : (
        <div style={{display:"flex",flexDirection:"column",gap:10}}>
          {(labs as LabResult[]).sort((a,b)=>b.receivedAt.localeCompare(a.receivedAt)).map(lab=>{
            const s = STATUS_LAB[lab.status] ?? STATUS_LAB.UNREVIEWED
            const isOpen = expanded===lab.id
            const markers = parseMarkers(lab.parsedMarkersJson)
            const abnormal = markers.filter(m=>m.flag && m.flag!=="NORMAL")
            const isInterpreting = interpreting===lab.id

            return (
              <div key={lab.id} style={{border:`1px solid ${s.border}`,borderLeft:`4px solid ${s.color}`,borderRadius:10,overflow:"hidden",background:"#fff"}}>
                {/* Header */}
                <div onClick={()=>setExpanded(isOpen?null:lab.id)}
                  style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"14px 18px",cursor:"pointer",background:isOpen?LIGHT:"#fff"}}>
                  <div style={{flex:1}}>
                    <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:3}}>
                      <FlaskConical size={14} color={s.color}/>
                      <span style={{fontWeight:700,fontSize:14,color:"#0F172A"}}>{lab.pdfFilename||`${lab.source} result`}</span>
                      <span style={{background:s.bg,color:s.color,padding:"1px 7px",borderRadius:20,fontSize:11,fontWeight:700,border:`1px solid ${s.border}`}}>{lab.status}</span>
                      {abnormal.length>0 && (
                        <span style={{background:"#FEF2F2",color:RED,padding:"1px 7px",borderRadius:20,fontSize:11,fontWeight:700,border:"1px solid #FECACA"}}>
                          {abnormal.length} abnormal
                        </span>
                      )}
                      {lab.consultationId && (
                        <span style={{background:"#DCFCE7",color:GREEN,padding:"1px 7px",borderRadius:20,fontSize:11,fontWeight:700}}>Filed</span>
                      )}
                    </div>
                    <div style={{fontSize:12,color:GRAY}}>
                      {lab.source}
                      {lab.labReference && ` · Ref: ${lab.labReference}`}
                      {lab.collectedAt && ` · Collected: ${fmtDT(lab.collectedAt)}`}
                      {` · Received: ${fmtDT(lab.receivedAt)}`}
                    </div>
                  </div>
                  <div style={{display:"flex",alignItems:"center",gap:6,flexShrink:0,marginLeft:12}}>
                    {lab.pdfUrl && (
                      <button onClick={e=>{e.stopPropagation();window.open(lab.pdfUrl,"_blank")}}
                        style={{display:"flex",alignItems:"center",gap:4,padding:"4px 10px",background:LIGHT,color:NAVY,border:`1px solid ${BORDER}`,borderRadius:6,fontSize:12,cursor:"pointer"}}>
                        <Download size={11}/> PDF
                      </button>
                    )}
                    {isOpen ? <ChevronUp size={16} color={GRAY}/> : <ChevronDown size={16} color={GRAY}/>}
                  </div>
                </div>

                {/* Expanded */}
                {isOpen && (
                  <div style={{borderTop:`1px solid ${BORDER}`,padding:"16px 18px",background:"#FAFAFA"}}>

                    {/* Markers table */}
                    {markers.length>0 && (
                      <div style={{marginBottom:16}}>
                        <div style={{fontSize:11,fontWeight:700,color:GRAY,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:8}}>Test results</div>
                        <div style={{border:`1px solid ${BORDER}`,borderRadius:8,overflow:"hidden"}}>
                          <table style={{width:"100%",borderCollapse:"collapse"}}>
                            <thead>
                              <tr style={{background:LIGHT}}>
                                {["Marker","Result","Unit","Ref range","Flag"].map(h=>(
                                  <th key={h} style={{padding:"7px 12px",textAlign:"left",fontSize:11,fontWeight:700,color:GRAY}}>{h}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {markers.map((m,i)=>{
                                const flag = m.flag && m.flag!=="NORMAL" ? FLAG_CFG[m.flag] : null
                                return (
                                  <tr key={i} style={{borderTop:i>0?`1px solid #F1F5F9`:"none",background:flag?"#FFFBEB":"#fff"}}>
                                    <td style={{padding:"8px 12px",fontSize:13,fontWeight:600,color:"#0F172A"}}>{m.marker}</td>
                                    <td style={{padding:"8px 12px",fontSize:13,fontWeight:flag?700:400,color:flag?flag.color:"#0F172A"}}>{m.value}</td>
                                    <td style={{padding:"8px 12px",fontSize:12,color:GRAY}}>{m.unit||"—"}</td>
                                    <td style={{padding:"8px 12px",fontSize:12,color:GRAY}}>{m.refRange||"—"}</td>
                                    <td style={{padding:"8px 12px"}}>
                                      {flag ? (
                                        <span style={{background:flag.bg,color:flag.color,padding:"2px 8px",borderRadius:20,fontSize:11,fontWeight:700,border:`1px solid ${flag.color}30`}}>
                                          {m.flag}
                                        </span>
                                      ) : <span style={{color:GREEN,fontSize:11,fontWeight:600}}>NORMAL</span>}
                                    </td>
                                  </tr>
                                )
                              })}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}

                    {/* AI Interpretation */}
                    <div style={{marginBottom:16}}>
                      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:8}}>
                        <div style={{fontSize:11,fontWeight:700,color:GRAY,textTransform:"uppercase",letterSpacing:"0.06em"}}>
                          AI interpretation
                        </div>
                        <button onClick={()=>interpretWithClaude(lab)} disabled={isInterpreting}
                          style={{display:"flex",alignItems:"center",gap:5,padding:"5px 12px",
                            background:isInterpreting?"#F1F5F9":"#F5F3FF",
                            color:isInterpreting?GRAY:PURPLE,
                            border:`1px solid ${isInterpreting?BORDER:"#DDD6FE"}`,
                            borderRadius:7,fontSize:12,fontWeight:600,cursor:isInterpreting?"wait":"pointer"}}>
                          {isInterpreting
                            ? <><Loader size={12} style={{animation:"spin 1s linear infinite"}}/> Interpreting...</>
                            : <><Sparkles size={12}/> {lab.interpretation?"Re-interpret":"Interpret with Claude"}</>
                          }
                        </button>
                      </div>
                      {lab.interpretation ? (
                        <div style={{padding:"12px 14px",background:"#F0FDF4",border:"1px solid #86EFAC",borderRadius:8,fontSize:13,color:"#0F172A",lineHeight:1.6}}>
                          <span style={{fontWeight:700,color:TEAL}}>Claude: </span>{lab.interpretation}
                        </div>
                      ) : (
                        <div style={{padding:"10px 14px",background:LIGHT,border:`1px dashed ${BORDER}`,borderRadius:8,fontSize:12,color:GRAY}}>
                          Click "Interpret with Claude" to get a plain-language summary of these results.
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div style={{display:"flex",gap:8,justifyContent:"flex-end"}}>
                      {lab.status==="UNREVIEWED" && (
                        <button onClick={()=>markReviewed.mutate(lab.id)} disabled={markReviewed.isPending}
                          style={{display:"flex",alignItems:"center",gap:5,padding:"6px 14px",background:"#FFFBEB",color:AMBER,border:"1px solid #FDE68A",borderRadius:7,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                          <Eye size={12}/> Mark reviewed
                        </button>
                      )}
                      {lab.status!=="FILED" && (
                        <button onClick={()=>setShowFile(lab.id)}
                          style={{display:"flex",alignItems:"center",gap:5,padding:"6px 14px",background:"#F0FDF4",color:GREEN,border:"1px solid #86EFAC",borderRadius:7,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                          <FileText size={12}/> File to consultation
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── File to consultation modal ─────────────────────────────────────── */}
      {showFile && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.5)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1200,backdropFilter:"blur(3px)"}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:480,boxShadow:"0 24px 64px rgba(0,0,0,0.22)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700,color:"#0F172A"}}>File to consultation</h3>
              <button onClick={()=>setShowFile(null)} style={{background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={18}/></button>
            </div>
            <p style={{fontSize:13,color:GRAY,marginBottom:16}}>Link this lab result to a consultation so it appears in the patient's history.</p>
            <div style={{display:"flex",flexDirection:"column",gap:8}}>
              {(consultations as Consultation[]).length===0
                ? <div style={{fontSize:13,color:GRAY,textAlign:"center",padding:16}}>No consultations found.</div>
                : (consultations as Consultation[]).sort((a,b)=>b.consultedAt.localeCompare(a.consultedAt)).map(c=>(
                  <button key={c.id} onClick={()=>fileResult.mutate({id:showFile!,consultationId:c.id})}
                    disabled={fileResult.isPending}
                    style={{padding:"12px 14px",border:`1px solid ${BORDER}`,borderRadius:8,background:"#fff",cursor:"pointer",textAlign:"left" as const}}
                    onMouseEnter={e=>(e.currentTarget as HTMLButtonElement).style.background=LIGHT}
                    onMouseLeave={e=>(e.currentTarget as HTMLButtonElement).style.background="#fff"}>
                    <div style={{fontWeight:600,fontSize:13,color:"#0F172A"}}>{c.chiefComplaint}</div>
                    <div style={{fontSize:11,color:GRAY,marginTop:2}}>{fmtDT(c.consultedAt)}</div>
                  </button>
                ))}
            </div>
            <div style={{display:"flex",justifyContent:"flex-end",marginTop:16}}>
              <button onClick={()=>setShowFile(null)} style={{padding:"8px 16px",border:`1px solid ${BORDER}`,borderRadius:8,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}}>Cancel</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Upload modal ─────────────────────────────────────────────────────── */}
      {showUpload && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.55)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1200,backdropFilter:"blur(3px)"}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:520,boxShadow:"0 24px 64px rgba(0,0,0,0.22)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:17,fontWeight:700,color:"#0F172A"}}>Upload lab result</h3>
              <button onClick={()=>setShowUpload(false)} style={{background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={20}/></button>
            </div>

            <div style={{display:"flex",flexDirection:"column",gap:14}}>
              <div>
                <label style={lbl}>Lab source</label>
                <div style={{display:"flex",gap:6,flexWrap:"wrap" as const}}>
                  {["AMPATH","LANCET","PATHCARE","VERMAAK","EMAIL","MANUAL"].map(s=>(
                    <button key={s} onClick={()=>setUploadForm(f=>({...f,source:s}))}
                      style={{padding:"5px 12px",borderRadius:20,border:`1.5px solid ${uploadForm.source===s?TEAL:BORDER}`,
                        background:uploadForm.source===s?"#F0FDF4":"#fff",
                        color:uploadForm.source===s?TEAL:GRAY,
                        fontSize:12,fontWeight:uploadForm.source===s?700:400,cursor:"pointer"}}>
                      {s}
                    </button>
                  ))}
                </div>
              </div>

              <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
                <div>
                  <label style={lbl}>Lab reference number</label>
                  <input value={uploadForm.labReference} onChange={e=>setUploadForm(f=>({...f,labReference:e.target.value}))} placeholder="e.g. AMP-2026-001234" style={sinp}/>
                </div>
                <div>
                  <label style={lbl}>Collection date</label>
                  <input type="date" value={uploadForm.collectedAt} onChange={e=>setUploadForm(f=>({...f,collectedAt:e.target.value}))} style={sinp}/>
                </div>
              </div>

              {/* File picker */}
              <div>
                <label style={lbl}>PDF file</label>
                <div style={{border:`2px dashed ${BORDER}`,borderRadius:10,padding:"20px",textAlign:"center" as const,cursor:"pointer",background:LIGHT}}
                  onClick={()=>fileRef.current?.click()}>
                  <Upload size={22} color={GRAY} style={{marginBottom:6}}/>
                  <div style={{fontSize:13,color:GRAY}}>Click to select PDF</div>
                  <div style={{fontSize:11,color:"#94A3B8",marginTop:3}}>Ampath, Lancet, Pathcare reports · PDF only</div>
                  <input ref={fileRef} type="file" accept=".pdf" style={{display:"none"}}
                    onChange={e=>{ const f=e.target.files?.[0]; if(f) setUploadForm(x=>({...x,pdfFilename:f.name})) }}/>
                </div>
                {uploadForm.pdfFilename && (
                  <div style={{marginTop:6,fontSize:13,color:GREEN,display:"flex",alignItems:"center",gap:5}}>
                    <CheckCircle size={13}/>{uploadForm.pdfFilename}
                  </div>
                )}
              </div>

              <div>
                <label style={lbl}>Notes (optional)</label>
                <input value={uploadForm.notes} onChange={e=>setUploadForm(f=>({...f,notes:e.target.value}))} placeholder="e.g. Requested by Dr. Khumalo" style={sinp}/>
              </div>
            </div>

            {uploadError && (
              <div style={{marginTop:10,padding:"8px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED,display:"flex",alignItems:"center",gap:6}}>
                <AlertCircle size={13}/>{uploadError}
              </div>
            )}

            <div style={{display:"flex",gap:10,justifyContent:"flex-end",marginTop:20}}>
              <button onClick={()=>setShowUpload(false)} style={btnCancel}>Cancel</button>
              <button onClick={async()=>{
                setUploading(true); setUploadError("")
                try {
                  await apiClient.post("/api/v1/clinic/lab/results",{
                    source: uploadForm.source,
                    labReference: uploadForm.labReference||null,
                    pdfFilename: uploadForm.pdfFilename||null,
                    collectedAt: uploadForm.collectedAt ? new Date(uploadForm.collectedAt).toISOString() : null,
                    patientNameRaw: patient.fullName,
                  })
                  // Link to patient immediately
                  qc.invalidateQueries({queryKey:["pf-labs",patient.id]})
                  setShowUpload(false)
                  setUploadForm({source:"MANUAL",labReference:"",pdfFilename:"",collectedAt:"",notes:""})
                } catch(e:any) {
                  setUploadError(e.response?.data?.message??"Upload failed")
                } finally { setUploading(false) }
              }} disabled={uploading} style={btnPrimary}>
                {uploading ? <><Loader size={13}/> Uploading...</> : "Upload result"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Spinner() { return <div style={{textAlign:"center",padding:40,color:GRAY}}>Loading...</div> }

const lbl:React.CSSProperties      = {display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}
const sinp:React.CSSProperties     = {width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:8,fontSize:14,outline:"none",background:"#fff"}
const btnPrimary:React.CSSProperties = {display:"flex",alignItems:"center",gap:6,background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 20px",fontSize:13,fontWeight:600,cursor:"pointer"}
const btnCancel:React.CSSProperties  = {padding:"9px 18px",border:`1px solid ${BORDER}`,borderRadius:9,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}
