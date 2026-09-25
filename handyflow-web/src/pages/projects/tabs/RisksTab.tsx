// src/pages/projects/tabs/RisksTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface Risk { id:string; riskNumber:string|null; title:string; description:string|null; category:string|null; probability:number; impact:number; riskScore:number; rating:string; status:string; mitigation:string|null; ownerName:string|null; reviewDate:string|null; isOhsa:boolean }

function unwrap<T>(r:any):T[]{const d=r?.data?.data??r?.data??[];return Array.isArray(d)?d as T[]:d?.content??[]}
const inp:React.CSSProperties={width:'100%',padding:'9px 12px',border:'1.5px solid var(--hf-border)',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'var(--hf-surface)'}
const RATING:{[k:string]:{bg:string;color:string}}={
  RED:{bg:'var(--hf-danger-soft)',color:'var(--hf-danger-text)'},AMBER:{bg:'var(--hf-warning-soft-strong)',color:'var(--hf-warning-text-deep)'},GREEN:{bg:'var(--hf-success-soft-strong)',color:'var(--hf-success-text-strong)'},
}
const CATS=['SAFETY','FINANCIAL','SCHEDULE','TECHNICAL','LEGAL','ENVIRONMENTAL']
const SCORES=[[1,2,3,4,5],[6,8,10,12,15],[9,12,15,16,20],[12,16,20,20,25]] // impact rows, prob cols

export function RisksTab({projectId}:{projectId:string}) {
  const qc=useQueryClient()
  const [showAdd,setShowAdd]=useState(false)
  const [selected,setSelected]=useState<Risk|null>(null)
  const [err,setErr]=useState('')
  const [action,setAction]=useState('')
  const [actionNotes,setActionNotes]=useState('')
  const initF=()=>({title:'',description:'',category:'SAFETY',probability:3,impact:3,mitigation:'',ownerName:'',isOhsa:false})
  const [form,setForm]=useState<any>(initF())
  const sf=(k:string,v:any)=>setForm((p:any)=>({...p,[k]:v}))

  const {data:risks=[],isLoading}=useQuery<Risk[]>({
    queryKey:['pm-risks',projectId],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/risks`);return unwrap<Risk>(r)},
    staleTime:30_000,
  })

  const addMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/risks`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-risks',projectId]});setShowAdd(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to log risk'),
  })
  const actionMut=useMutation({
    mutationFn:({riskId,action,notes}:{riskId:string;action:string;notes:string})=>
      apiClient.post(`/api/v1/projects/risks/${riskId}/action`,{action,notes}),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-risks',projectId]});setSelected(null);setAction('');setActionNotes('')},
  })

  const openRisks=risks.filter(r=>r.status==='OPEN')
  const redCount=openRisks.filter(r=>r.rating==='RED').length
  const amberCount=openRisks.filter(r=>r.rating==='AMBER').length

  return (
    <div>
      {/* Summary pills */}
      <div style={{display:'flex',gap:10,marginBottom:16}}>
        {[{label:`${redCount} Red`,bg:'var(--hf-danger-soft)',color:'var(--hf-danger-text)'},{label:`${amberCount} Amber`,bg:'var(--hf-warning-soft-strong)',color:'var(--hf-warning-text-deep)'},{label:`${openRisks.length} Open`,bg:'var(--hf-surface-sunken)',color:'var(--hf-text-tertiary)'}].map(s=>(
          <div key={s.label} style={{background:s.bg,color:s.color,fontSize:12,fontWeight:700,padding:'5px 12px',borderRadius:20}}>{s.label}</div>
        ))}
        <div style={{flex:1}}/>
        <button onClick={()=>{setShowAdd(true);setErr('')}} style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Log Risk
        </button>
      </div>

      {isLoading?<div style={{padding:40,textAlign:'center',color:'var(--hf-text-faint)'}}>Loading…</div>
        :risks.length===0?<div style={{textAlign:'center',padding:'50px 20px',color:'var(--hf-text-faint)'}}><AlertTriangle size={36} style={{marginBottom:10,opacity:.3}}/><div style={{fontWeight:600,color:'var(--hf-text-tertiary)'}}>No risks logged</div><div style={{fontSize:13}}>Log risks to track and mitigate project threats</div></div>
        :(
        <div style={{border:'1px solid var(--hf-border)',borderRadius:12,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead><tr style={{background:'var(--hf-surface-muted)'}}>
              {['Rating','Score','Title','Category','P×I','Owner','Review','Status',''].map(h=>(
                <th key={h} style={{padding:'10px 14px',textAlign:'left' as const,fontSize:10,fontWeight:700,color:'var(--hf-text-faint)',textTransform:'uppercase' as const,letterSpacing:'0.04em'}}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {risks.map((r,i)=>{
                const rt=RATING[r.rating]??RATING.GREEN
                return (
                  <tr key={r.id} style={{borderTop:'1px solid var(--hf-border-subtle)',background:i%2===0?'var(--hf-surface)':'var(--hf-surface-muted)',cursor:'pointer'}} onClick={()=>setSelected(r)}>
                    <td style={{padding:'10px 14px'}}><span style={{background:rt.bg,color:rt.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>{r.rating}</span></td>
                    <td style={{padding:'10px 14px',fontSize:15,fontWeight:800,color:rt.color}}>{r.riskScore}</td>
                    <td style={{padding:'10px 14px',fontSize:13,fontWeight:600,color:'var(--hf-text)',maxWidth:200,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap' as const}}>
                      {r.isOhsa&&<span title="OHSA" style={{marginRight:5}}>🦺</span>}{r.title}
                    </td>
                    <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{r.category??'—'}</td>
                    <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{r.probability}×{r.impact}</td>
                    <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{r.ownerName??'—'}</td>
                    <td style={{padding:'10px 14px',fontSize:11,color:'var(--hf-text-muted)'}}>{r.reviewDate?new Date(r.reviewDate).toLocaleDateString('en-ZA'):'—'}</td>
                    <td style={{padding:'10px 14px'}}><span style={{fontSize:10,fontWeight:600,color:'var(--hf-text-muted)'}}>{r.status}</span></td>
                    <td style={{padding:'10px 14px',fontSize:11,color:'var(--hf-info-text)',fontWeight:600}}>Actions →</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Risk detail / action modal */}
      {selected&&(
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'var(--hf-surface)',borderRadius:14,padding:28,width:520,boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>{selected.title}</h3>
              <button onClick={()=>setSelected(null)} style={{background:'none',border:'none',cursor:'pointer',color:'var(--hf-text-faint)',fontSize:20}}>×</button>
            </div>
            {selected.description&&<p style={{fontSize:13,color:'var(--hf-text-muted)',marginBottom:12}}>{selected.description}</p>}
            {selected.mitigation&&<div style={{background:'var(--hf-success-soft)',border:'1px solid var(--hf-success-border-subtle)',borderRadius:8,padding:'10px 12px',marginBottom:12,fontSize:13,color:'var(--hf-success-text-strong)'}}>Mitigation: {selected.mitigation}</div>}
            {selected.status==='OPEN'&&(
              <div style={{borderTop:'1px solid var(--hf-border)',paddingTop:14}}>
                <div style={{fontSize:13,fontWeight:600,marginBottom:8}}>Update Risk</div>
                <div style={{display:'flex',gap:8,marginBottom:10}}>
                  {['MITIGATE','ACCEPT','CLOSE'].map(a=>(
                    <button key={a} onClick={()=>setAction(a)} style={{padding:'6px 12px',border:action===a?'1.5px solid var(--hf-primary)':'1px solid var(--hf-border)',borderRadius:8,background:action===a?'var(--hf-info-soft)':'var(--hf-surface)',color:action===a?'var(--hf-primary-text)':'var(--hf-text-muted)',fontSize:12,fontWeight:600,cursor:'pointer'}}>{a}</button>
                  ))}
                </div>
                {action&&<><textarea value={actionNotes} onChange={e=>setActionNotes(e.target.value)} placeholder={`Notes for ${action.toLowerCase()} action…`} style={{...inp,minHeight:60,resize:'vertical' as const,marginBottom:10}}/><button onClick={()=>actionMut.mutate({riskId:selected.id,action,notes:actionNotes})} style={{padding:'8px 16px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:8,fontSize:13,fontWeight:600,cursor:'pointer'}}>Save Action</button></>}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Add Risk Modal */}
      {showAdd&&(
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'var(--hf-surface)',borderRadius:14,padding:28,width:540,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Log Risk</h3>
              <button onClick={()=>setShowAdd(false)} style={{background:'none',border:'none',cursor:'pointer',color:'var(--hf-text-faint)',fontSize:20}}>×</button>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Title *</label><input value={form.title} onChange={(e:any)=>sf('title',e.target.value)} style={inp} placeholder="Risk description" autoFocus/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Category</label><select value={form.category} onChange={(e:any)=>sf('category',e.target.value)} style={inp}>{CATS.map(c=><option key={c} value={c}>{c}</option>)}</select></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Owner</label><input value={form.ownerName} onChange={(e:any)=>sf('ownerName',e.target.value)} style={inp} placeholder="Risk owner"/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Probability (1–5)</label>
                <input type="range" min={1} max={5} value={form.probability} onChange={(e:any)=>sf('probability',parseInt(e.target.value))} style={{width:'100%'}}/>
                <div style={{fontSize:12,color:'var(--hf-text-muted)',textAlign:'center' as const}}>{form.probability}/5</div>
              </div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Impact (1–5)</label>
                <input type="range" min={1} max={5} value={form.impact} onChange={(e:any)=>sf('impact',parseInt(e.target.value))} style={{width:'100%'}}/>
                <div style={{fontSize:12,color:'var(--hf-text-muted)',textAlign:'center' as const}}>{form.impact}/5</div>
              </div>
              <div style={{gridColumn:'span 2',padding:'8px 12px',borderRadius:8,background:(()=>{const s=form.probability*form.impact;return s>=15?'#FEF2F2':s>=9?'#FEF3C7':'#DCFCE7'})()}}>
                <span style={{fontSize:12,fontWeight:700,color:(()=>{const s=form.probability*form.impact;return s>=15?'#DC2626':s>=9?'#92400E':'#166534'})()}}>
                  Risk Score: {form.probability*form.impact} — {form.probability*form.impact>=15?'RED':form.probability*form.impact>=9?'AMBER':'GREEN'}
                </span>
              </div>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Mitigation</label><textarea value={form.mitigation} onChange={(e:any)=>sf('mitigation',e.target.value)} style={{...inp,minHeight:60,resize:'vertical' as const}} placeholder="How will this risk be managed?"/></div>
              <div style={{gridColumn:'span 2'}}><label style={{display:'flex',alignItems:'center',gap:8,fontSize:13,color:'var(--hf-text-secondary)',cursor:'pointer'}}><input type="checkbox" checked={form.isOhsa} onChange={(e:any)=>sf('isOhsa',e.target.checked)}/> 🦺 OHSA Act 85 — Health &amp; Safety risk</label></div>
            </div>
            {err&&<div style={{marginTop:10,padding:'8px 12px',background:'var(--hf-danger-soft)',border:'1px solid var(--hf-danger-border)',borderRadius:8,color:'var(--hf-danger-text)',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowAdd(false)} style={{padding:'9px 16px',border:'1px solid var(--hf-border)',borderRadius:9,background:'var(--hf-surface)',fontSize:13,cursor:'pointer'}}>Cancel</button>
              <button onClick={()=>{if(!form.title.trim()){setErr('Title required');return}addMut.mutate({title:form.title.trim(),description:form.description||null,category:form.category,probability:form.probability,impact:form.impact,mitigation:form.mitigation||null,ownerName:form.ownerName||null,isOhsa:form.isOhsa})}} disabled={addMut.isPending} style={{padding:'9px 16px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:addMut.isPending?.6:1}}>{addMut.isPending?'Saving…':'Log Risk'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
