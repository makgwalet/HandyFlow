// src/pages/projects/tabs/RfiTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, MessageSquare, CheckCircle, Clock, XCircle, Send } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface Rfi {
  id: string; rfiNumber: string; title: string; description: string | null
  category: string | null; requestedBy: string | null; requestedDate: string
  dueDate: string | null; respondedBy: string | null; respondedDate: string | null
  response: string | null; status: string; createdAt: string
}

function unwrap<T>(r:any):T[]{const d=r?.data?.data??r?.data??[];return Array.isArray(d)?d as T[]:d?.content??[]}
const fmtDate = (d:string|null) => d ? new Date(d).toLocaleDateString('en-ZA') : '—'
const inp:React.CSSProperties = {width:'100%',padding:'9px 12px',border:'1.5px solid var(--hf-border)',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'var(--hf-surface)'}

const STATUS_CONFIG: Record<string,{bg:string;color:string;icon:React.ElementType;label:string}> = {
  DRAFT:     {bg:'var(--hf-surface-sunken)',color:'var(--hf-text-tertiary)',   icon:Clock,        label:'Draft'},
  SUBMITTED: {bg:'var(--hf-info-soft-strong)',color:'var(--hf-info-text)',   icon:Send,         label:'Submitted'},
  RESPONDED: {bg:'var(--hf-warning-soft-strong)',color:'var(--hf-warning-text-deep)',   icon:MessageSquare,label:'Responded'},
  CLOSED:    {bg:'var(--hf-success-soft-strong)',color:'var(--hf-success-text-strong)',   icon:CheckCircle,  label:'Closed'},
  CANCELLED: {bg:'var(--hf-surface-sunken)',color:'var(--hf-text-faint)',   icon:XCircle,      label:'Cancelled'},
}

const CATEGORIES = ['DESIGN','SITE','MATERIALS','SAFETY','SPECIFICATION','OTHER']

export function RfiTab({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<Rfi | null>(null)
  const [responseText, setResponse] = useState('')
  const [err, setErr]               = useState('')
  const initF = () => ({ title:'', description:'', category:'SITE', requestedBy:'', dueDate:'', submitImmediately: false })
  const [form, setForm] = useState(initF())
  const sf = (k:string, v:any) => setForm(p => ({...p,[k]:v}))

  const { data:rfis=[], isLoading } = useQuery<Rfi[]>({
    queryKey: ['pm-rfis', projectId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/projects/${projectId}/rfis`); return unwrap<Rfi>(r) },
    staleTime: 30_000,
  })

  const createMut = useMutation({
    mutationFn: (body:any) => apiClient.post(`/api/v1/projects/${projectId}/rfis`, body),
    onSuccess: () => { qc.invalidateQueries({queryKey:['pm-rfis',projectId]}); setShowCreate(false); setForm(initF()); setErr('') },
    onError: (e:any) => setErr(e.response?.data?.message || 'Failed to create RFI'),
  })

  const transition = (action: string, body?: any) => apiClient.post(
    `/api/v1/projects/rfis/${selected!.id}/${action}`, body
  )

  const submitMut = useMutation({
    mutationFn: () => transition('submit'),
    onSuccess: () => { qc.invalidateQueries({queryKey:['pm-rfis',projectId]}); setSelected(null) },
  })
  const respondMut = useMutation({
    mutationFn: () => transition('respond', { response: responseText }),
    onSuccess: () => { qc.invalidateQueries({queryKey:['pm-rfis',projectId]}); setSelected(null); setResponse('') },
  })
  const closeMut = useMutation({
    mutationFn: () => transition('close'),
    onSuccess: () => { qc.invalidateQueries({queryKey:['pm-rfis',projectId]}); setSelected(null) },
  })
  const cancelMut = useMutation({
    mutationFn: () => transition('cancel'),
    onSuccess: () => { qc.invalidateQueries({queryKey:['pm-rfis',projectId]}); setSelected(null) },
  })

  const open    = rfis.filter(r => r.status === 'SUBMITTED' || r.status === 'RESPONDED').length
  const pending = rfis.filter(r => r.status === 'SUBMITTED').length

  return (
    <div>
      {/* Summary + toolbar */}
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,flexWrap:'wrap',gap:10}}>
        <div style={{display:'flex',gap:8}}>
          {[
            {label:`${pending} Pending Response`, bg:'var(--hf-info-soft-strong)', color:'var(--hf-info-text)'},
            {label:`${open} Open`, bg:'var(--hf-warning-soft-strong)', color:'var(--hf-warning-text-deep)'},
            {label:`${rfis.length} Total`, bg:'var(--hf-surface-sunken)', color:'var(--hf-text-tertiary)'},
          ].map(s=>(
            <div key={s.label} style={{background:s.bg,color:s.color,fontSize:12,fontWeight:700,padding:'5px 12px',borderRadius:20}}>{s.label}</div>
          ))}
        </div>
        <button onClick={()=>{setShowCreate(true);setErr('')}}
          style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> New RFI
        </button>
      </div>

      {/* RFI list */}
      {isLoading ? <div style={{padding:40,textAlign:'center',color:'var(--hf-text-faint)'}}>Loading…</div>
        : rfis.length === 0
          ? <div style={{textAlign:'center',padding:'50px 20px',color:'var(--hf-text-faint)'}}>
              <MessageSquare size={36} style={{marginBottom:10,opacity:.3}}/>
              <div style={{fontWeight:600,color:'var(--hf-text-tertiary)'}}>No RFIs logged</div>
              <div style={{fontSize:13}}>Create an RFI to track requests for information from the design team or client</div>
            </div>
          : (
            <div style={{border:'1px solid var(--hf-border)',borderRadius:12,overflow:'hidden'}}>
              <table style={{width:'100%',borderCollapse:'collapse'}}>
                <thead><tr style={{background:'var(--hf-surface-muted)'}}>
                  {['#','Title','Category','Requested By','Due','Status','Responded By',''].map(h=>(
                    <th key={h} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'var(--hf-text-faint)',textTransform:'uppercase',letterSpacing:'0.04em'}}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {rfis.map((r,i) => {
                    const sc = STATUS_CONFIG[r.status] ?? STATUS_CONFIG.DRAFT
                    const Icon = sc.icon
                    return (
                      <tr key={r.id} onClick={()=>{ setSelected(r); setResponse('') }}
                        style={{borderTop:'1px solid var(--hf-border-subtle)',background:i%2===0?'var(--hf-surface)':'var(--hf-surface-muted)',cursor:'pointer'}}
                        onMouseEnter={e=>(e.currentTarget.style.background='#F0F7FF')}
                        onMouseLeave={e=>(e.currentTarget.style.background=i%2===0?'var(--hf-surface)':'var(--hf-surface-muted)')}>
                        <td style={{padding:'10px 14px',fontSize:12,fontWeight:700,color:'var(--hf-primary-text)',whiteSpace:'nowrap'}}>{r.rfiNumber}</td>
                        <td style={{padding:'10px 14px',fontSize:13,fontWeight:600,color:'var(--hf-text)',maxWidth:220,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{r.title}</td>
                        <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{r.category ?? '—'}</td>
                        <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{r.requestedBy ?? '—'}</td>
                        <td style={{padding:'10px 14px',fontSize:11,color:r.dueDate && new Date(r.dueDate) < new Date() && r.status === 'SUBMITTED' ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)',whiteSpace:'nowrap'}}>{fmtDate(r.dueDate)}</td>
                        <td style={{padding:'10px 14px'}}>
                          <span style={{display:'inline-flex',alignItems:'center',gap:4,background:sc.bg,color:sc.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>
                            <Icon size={10}/>{sc.label}
                          </span>
                        </td>
                        <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{r.respondedBy ?? '—'}</td>
                        <td style={{padding:'10px 14px',fontSize:11,color:'var(--hf-info-text)',fontWeight:600}}>Open →</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )
      }

      {/* RFI detail / action modal */}
      {selected && (
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'var(--hf-surface)',borderRadius:14,padding:28,width:580,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',marginBottom:16}}>
              <div>
                <div style={{fontSize:11,fontWeight:700,color:'var(--hf-primary-text)',marginBottom:4}}>{selected.rfiNumber}</div>
                <h3 style={{margin:0,fontSize:16,fontWeight:700,color:'var(--hf-text)'}}>{selected.title}</h3>
              </div>
              <button onClick={()=>setSelected(null)} style={{background:'none',border:'none',cursor:'pointer',color:'var(--hf-text-faint)',fontSize:20,lineHeight:1}}>×</button>
            </div>

            {/* Meta */}
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:8,marginBottom:16}}>
              {[
                ['Category',     selected.category ?? '—'],
                ['Requested By', selected.requestedBy ?? '—'],
                ['Requested',    fmtDate(selected.requestedDate)],
                ['Due Date',     fmtDate(selected.dueDate)],
              ].map(([k,v])=>(
                <div key={k} style={{background:'var(--hf-surface-muted)',borderRadius:8,padding:'8px 12px'}}>
                  <div style={{fontSize:10,color:'var(--hf-text-faint)',fontWeight:600,textTransform:'uppercase',letterSpacing:'0.04em',marginBottom:2}}>{k}</div>
                  <div style={{fontSize:13,fontWeight:600,color:'var(--hf-text)'}}>{v}</div>
                </div>
              ))}
            </div>

            {/* Description */}
            {selected.description && (
              <div style={{marginBottom:14}}>
                <div style={{fontSize:12,fontWeight:700,color:'var(--hf-text-secondary)',marginBottom:6}}>Description</div>
                <div style={{fontSize:13,color:'var(--hf-text-muted)',lineHeight:1.6,background:'var(--hf-surface-muted)',borderRadius:8,padding:'10px 12px'}}>{selected.description}</div>
              </div>
            )}

            {/* Response (if responded/closed) */}
            {selected.response && (
              <div style={{marginBottom:14,background:'var(--hf-success-soft)',border:'1px solid var(--hf-success-border-subtle)',borderRadius:8,padding:'12px 14px'}}>
                <div style={{fontSize:12,fontWeight:700,color:'var(--hf-success-text-strong)',marginBottom:4}}>
                  Response — {selected.respondedBy} ({fmtDate(selected.respondedDate)})
                </div>
                <div style={{fontSize:13,color:'var(--hf-success-text-strong)',lineHeight:1.6}}>{selected.response}</div>
              </div>
            )}

            {/* Actions */}
            <div style={{borderTop:'1px solid var(--hf-border)',paddingTop:14,marginTop:8}}>
              <div style={{fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:10}}>Actions</div>
              <div style={{display:'flex',flexDirection:'column',gap:10}}>

                {selected.status === 'DRAFT' && (
                  <button onClick={()=>submitMut.mutate()} disabled={submitMut.isPending}
                    style={{display:'flex',alignItems:'center',gap:6,padding:'9px 14px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:8,fontSize:13,fontWeight:600,cursor:'pointer',opacity:submitMut.isPending?.6:1}}>
                    <Send size={13}/> Submit RFI
                  </button>
                )}

                {selected.status === 'SUBMITTED' && (
                  <div>
                    <label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Respond *</label>
                    <textarea value={responseText} onChange={e=>setResponse(e.target.value)}
                      placeholder="Provide your response to this RFI…"
                      style={{...inp,minHeight:80,resize:'vertical',marginBottom:8}}/>
                    <button onClick={()=>{ if(!responseText.trim()) return; respondMut.mutate() }} disabled={respondMut.isPending || !responseText.trim()}
                      style={{display:'flex',alignItems:'center',gap:6,padding:'9px 14px',background:'var(--hf-success)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:8,fontSize:13,fontWeight:600,cursor:'pointer',opacity:(respondMut.isPending||!responseText.trim())?.6:1}}>
                      <MessageSquare size={13}/> Submit Response
                    </button>
                  </div>
                )}

                {selected.status === 'RESPONDED' && (
                  <button onClick={()=>closeMut.mutate()} disabled={closeMut.isPending}
                    style={{display:'flex',alignItems:'center',gap:6,padding:'9px 14px',background:'var(--hf-success-soft-strong)',color:'var(--hf-success-text-strong)',border:'1px solid var(--hf-success-border)',borderRadius:8,fontSize:13,fontWeight:600,cursor:'pointer',opacity:closeMut.isPending?.6:1}}>
                    <CheckCircle size={13}/> Accept Response &amp; Close RFI
                  </button>
                )}

                {['DRAFT','SUBMITTED','RESPONDED'].includes(selected.status) && (
                  <button onClick={()=>cancelMut.mutate()} disabled={cancelMut.isPending}
                    style={{display:'flex',alignItems:'center',gap:6,padding:'8px 14px',background:'none',color:'var(--hf-danger-text)',border:'1px solid var(--hf-danger-border)',borderRadius:8,fontSize:12,cursor:'pointer',opacity:cancelMut.isPending?.6:1}}>
                    <XCircle size={12}/> Cancel RFI
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Create RFI modal */}
      {showCreate && (
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'var(--hf-surface)',borderRadius:14,padding:28,width:540,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>New RFI</h3>
              <button onClick={()=>setShowCreate(false)} style={{background:'none',border:'none',cursor:'pointer',color:'var(--hf-text-faint)',fontSize:20,lineHeight:1}}>×</button>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
              <div style={{gridColumn:'span 2'}}>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Title *</label>
                <input value={form.title} onChange={e=>sf('title',e.target.value)} placeholder="Clarification on structural drawings rev B" style={inp} autoFocus/>
              </div>
              <div>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Category</label>
                <select value={form.category} onChange={e=>sf('category',e.target.value)} style={inp}>
                  {CATEGORIES.map(c=><option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Due Date</label>
                <input type="date" value={form.dueDate} onChange={e=>sf('dueDate',e.target.value)} style={inp}/>
              </div>
              <div style={{gridColumn:'span 2'}}>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Requested By</label>
                <input value={form.requestedBy} onChange={e=>sf('requestedBy',e.target.value)} placeholder="Name of person requesting information" style={inp}/>
              </div>
              <div style={{gridColumn:'span 2'}}>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>Description</label>
                <textarea value={form.description} onChange={e=>sf('description',e.target.value)} placeholder="Detailed description of the information required…" style={{...inp,minHeight:80,resize:'vertical'}}/>
              </div>
              <div style={{gridColumn:'span 2'}}>
                <label style={{display:'flex',alignItems:'center',gap:8,fontSize:13,color:'var(--hf-text-secondary)',cursor:'pointer'}}>
                  <input type="checkbox" checked={form.submitImmediately} onChange={e=>sf('submitImmediately',e.target.checked)}/>
                  Submit immediately (skip Draft status)
                </label>
              </div>
            </div>
            {err && <div style={{marginTop:10,padding:'8px 12px',background:'var(--hf-danger-soft)',border:'1px solid var(--hf-danger-border)',borderRadius:8,color:'var(--hf-danger-text)',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowCreate(false)} style={{padding:'9px 16px',border:'1px solid var(--hf-border)',borderRadius:9,background:'var(--hf-surface)',fontSize:13,cursor:'pointer',color:'var(--hf-text-muted)'}}>Cancel</button>
              <button onClick={()=>{if(!form.title.trim()){setErr('Title required');return}createMut.mutate({title:form.title.trim(),description:form.description||null,category:form.category,requestedBy:form.requestedBy||null,dueDate:form.dueDate||null,submitImmediately:form.submitImmediately})}}
                disabled={createMut.isPending}
                style={{padding:'9px 18px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:createMut.isPending?.6:1}}>
                {createMut.isPending ? 'Creating…' : 'Create RFI'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
