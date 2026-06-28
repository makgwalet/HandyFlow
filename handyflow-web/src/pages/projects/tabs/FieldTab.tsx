// src/pages/projects/tabs/FieldTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, BookOpen, AlertTriangle, CheckCircle, Camera } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface SiteDiary { id:string; diaryDate:string; weather:string|null; tempCelsius:number|null; workersPresent:number; workersPlanned:number|null; workDescription:string|null; progressNotes:string|null; issues:string|null; toolboxTopic:string|null; incidents:string|null; submittedByName:string|null }
interface Snag { id:string; snagNumber:string; title:string; description:string|null; location:string|null; severity:string; status:string; assignedToName:string|null; dueDate:string|null; photoUrls:string[]|null; createdAt:string }

function unwrap<T>(r:any):T[]{const d=r?.data?.data??r?.data??[];return Array.isArray(d)?d as T[]:d?.content??[]}
const inp:React.CSSProperties={width:'100%',padding:'9px 12px',border:'1.5px solid #E2E8F0',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'#fff'}
const SEVERITY:{[k:string]:{bg:string;color:string}}={LOW:{bg:'#F1F5F9',color:'#475569'},MEDIUM:{bg:'#FEF3C7',color:'#92400E'},HIGH:{bg:'#FEF2F2',color:'#DC2626'},CRITICAL:{bg:'#FEF2F2',color:'#7F1D1D'}}
const SNAG_STATUS:{[k:string]:{bg:string;color:string}}={OPEN:{bg:'#FEF2F2',color:'#DC2626'},IN_PROGRESS:{bg:'#FEF3C7',color:'#92400E'},RESOLVED:{bg:'#DCFCE7',color:'#166534'},REJECTED:{bg:'#F1F5F9',color:'#9CA3AF'}}

export function FieldTab({projectId}:{projectId:string}) {
  const [panel,setPanel]=useState<'diaries'|'snags'>('diaries')
  return (
    <div>
      <div style={{display:'flex',gap:8,marginBottom:20}}>
        {([['diaries','📒 Site Diaries'],['snags','🔧 Snag List']] as const).map(([k,l])=>(
          <button key={k} onClick={()=>setPanel(k)}
            style={{padding:'8px 16px',borderRadius:8,border:panel===k?'1.5px solid #1B3A6B':'1px solid #E2E8F0',background:panel===k?'#EFF6FF':'#fff',color:panel===k?'#1B3A6B':'#64748B',fontSize:13,fontWeight:panel===k?700:500,cursor:'pointer'}}>
            {l}
          </button>
        ))}
      </div>
      {panel==='diaries'?<DiariesPanel projectId={projectId}/>:<SnagsPanel projectId={projectId}/>}
    </div>
  )
}

function DiariesPanel({projectId}:{projectId:string}) {
  const qc=useQueryClient()
  const [showAdd,setShowAdd]=useState(false)
  const [err,setErr]=useState('')
  const today=new Date().toISOString().split('T')[0]
  const initF=()=>({diaryDate:today,weather:'CLEAR',tempCelsius:'',workersPresent:'0',workersPlanned:'',workDescription:'',progressNotes:'',issues:'',toolboxTopic:'',incidents:'',equipmentNotes:''})
  const [form,setForm]=useState(initF())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:diaries=[],isLoading}=useQuery<SiteDiary[]>({
    queryKey:['pm-diaries',projectId],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/site-diaries`);return unwrap<SiteDiary>(r)},
    staleTime:60_000,
  })

  const addMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/site-diaries`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-diaries',projectId]});setShowAdd(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to submit diary — one already exists for this date?'),
  })

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16}}>
        <div style={{fontSize:13,color:'#64748B'}}>{diaries.length} site diaries submitted</div>
        <button onClick={()=>{setShowAdd(true);setErr('')}} style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Today's Diary
        </button>
      </div>

      {isLoading?<div style={{padding:40,textAlign:'center',color:'#94A3B8'}}>Loading…</div>
        :diaries.length===0?<Empty icon={BookOpen} text="No site diaries" sub="Submit your first daily site diary"/>
        :(
        <div style={{display:'flex',flexDirection:'column' as const,gap:10}}>
          {diaries.map(d=>(
            <div key={d.id} style={{background:'#fff',border:'1px solid #E2E8F0',borderRadius:10,padding:'14px 16px'}}>
              <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:8}}>
                <div style={{fontSize:14,fontWeight:700,color:'#0F172A'}}>{new Date(d.diaryDate).toLocaleDateString('en-ZA',{weekday:'long',day:'numeric',month:'long'})}</div>
                <div style={{display:'flex',gap:10,fontSize:12,color:'#64748B'}}>
                  {d.weather&&<span>🌤 {d.weather}</span>}
                  {d.tempCelsius!=null&&<span>{d.tempCelsius}°C</span>}
                  <span>👷 {d.workersPresent}{d.workersPlanned?`/${d.workersPlanned}`:''}</span>
                </div>
              </div>
              <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:8}}>
                {d.workDescription&&<DiaryField label="Work Done" value={d.workDescription}/>}
                {d.progressNotes&&<DiaryField label="Progress" value={d.progressNotes}/>}
                {d.issues&&<DiaryField label="Issues" value={d.issues} warn/>}
                {d.toolboxTopic&&<DiaryField label="Toolbox Topic" value={d.toolboxTopic}/>}
                {d.incidents&&<DiaryField label="⚠ Incidents" value={d.incidents} warn/>}
              </div>
              {d.submittedByName&&<div style={{fontSize:11,color:'#94A3B8',marginTop:8}}>Submitted by {d.submittedByName}</div>}
            </div>
          ))}
        </div>
      )}

      {showAdd&&(
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'#fff',borderRadius:14,padding:28,width:600,maxHeight:'92vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Daily Site Diary</h3>
              <button onClick={()=>setShowAdd(false)} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',fontSize:20}}>×</button>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr 1fr',gap:12}}>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Date</label><input type="date" value={form.diaryDate} onChange={e=>sf('diaryDate',e.target.value)} style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Weather</label>
                <select value={form.weather} onChange={e=>sf('weather',e.target.value)} style={inp}>
                  {['CLEAR','CLOUDY','RAIN','STORM','WIND'].map(w=><option key={w} value={w}>{w}</option>)}
                </select>
              </div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Temp °C</label><input type="number" value={form.tempCelsius} onChange={e=>sf('tempCelsius',e.target.value)} placeholder="22" style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Workers Present *</label><input type="number" value={form.workersPresent} onChange={e=>sf('workersPresent',e.target.value)} style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Workers Planned</label><input type="number" value={form.workersPlanned} onChange={e=>sf('workersPlanned',e.target.value)} style={inp}/></div>
              <div style={{gridColumn:'span 3'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Work Done Today</label><textarea value={form.workDescription} onChange={e=>sf('workDescription',e.target.value)} style={{...inp,minHeight:60,resize:'vertical' as const}} placeholder="Describe work activities completed…"/></div>
              <div style={{gridColumn:'span 3'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Progress Notes</label><textarea value={form.progressNotes} onChange={e=>sf('progressNotes',e.target.value)} style={{...inp,minHeight:50,resize:'vertical' as const}} placeholder="Progress vs plan…"/></div>
              <div style={{gridColumn:'span 3'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Issues / Delays</label><textarea value={form.issues} onChange={e=>sf('issues',e.target.value)} style={{...inp,minHeight:50,resize:'vertical' as const}} placeholder="Any issues, delays or constraints…"/></div>
              <div style={{gridColumn:'span 3'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Toolbox Talk Topic</label><input value={form.toolboxTopic} onChange={e=>sf('toolboxTopic',e.target.value)} placeholder="OHSA safety topic discussed" style={inp}/></div>
              <div style={{gridColumn:'span 3'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Incidents (if any)</label><textarea value={form.incidents} onChange={e=>sf('incidents',e.target.value)} style={{...inp,minHeight:50,resize:'vertical' as const}} placeholder="Near misses, injuries, property damage…"/></div>
            </div>
            {err&&<div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowAdd(false)} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer'}}>Cancel</button>
              <button onClick={()=>{addMut.mutate({diaryDate:form.diaryDate,weather:form.weather,tempCelsius:form.tempCelsius?parseFloat(form.tempCelsius):null,workersPresent:parseInt(form.workersPresent)||0,workersPlanned:form.workersPlanned?parseInt(form.workersPlanned):null,workDescription:form.workDescription||null,progressNotes:form.progressNotes||null,issues:form.issues||null,toolboxTopic:form.toolboxTopic||null,incidents:form.incidents||null,equipmentNotes:null,visitorNames:null})}} disabled={addMut.isPending} style={{padding:'9px 16px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:addMut.isPending?.6:1}}>{addMut.isPending?'Submitting…':'Submit Diary'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function SnagsPanel({projectId}:{projectId:string}) {
  const qc=useQueryClient()
  const [showAdd,setShowAdd]=useState(false)
  const [openOnly,setOpenOnly]=useState(false)
  const [err,setErr]=useState('')
  const initF=()=>({title:'',description:'',location:'',severity:'MEDIUM',assignedToName:'',dueDate:''})
  const [form,setForm]=useState(initF())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:snags=[],isLoading}=useQuery<Snag[]>({
    queryKey:['pm-snags',projectId,openOnly],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/snags?openOnly=${openOnly}`);return unwrap<Snag>(r)},
    staleTime:30_000,
  })

  const addMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/snags`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-snags',projectId]});setShowAdd(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to log snag'),
  })
  const actionMut=useMutation({
    mutationFn:({snagId,action}:{snagId:string;action:string})=>apiClient.post(`/api/v1/projects/snags/${snagId}/${action}`),
    onSuccess:()=>qc.invalidateQueries({queryKey:['pm-snags',projectId]}),
  })

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,flexWrap:'wrap' as const,gap:8}}>
        <label style={{display:'flex',alignItems:'center',gap:8,fontSize:13,cursor:'pointer'}}>
          <input type="checkbox" checked={openOnly} onChange={e=>setOpenOnly(e.target.checked)}/> Open snags only
        </label>
        <button onClick={()=>{setShowAdd(true);setErr('')}} style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Log Snag
        </button>
      </div>

      {isLoading?<div style={{padding:40,textAlign:'center',color:'#94A3B8'}}>Loading…</div>
        :snags.length===0?<Empty icon={AlertTriangle} text="No snags" sub="Log quality issues and defects as snag items"/>
        :(
        <div style={{display:'flex',flexDirection:'column' as const,gap:8}}>
          {snags.map(s=>{
            const sv=SEVERITY[s.severity]??SEVERITY.MEDIUM
            const st=SNAG_STATUS[s.status]??SNAG_STATUS.OPEN
            return (
              <div key={s.id} style={{background:'#fff',border:'1px solid #E2E8F0',borderRadius:10,padding:'12px 14px'}}>
                <div style={{display:'flex',alignItems:'flex-start',gap:10}}>
                  <div style={{flex:1}}>
                    <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:4}}>
                      <span style={{fontSize:11,color:'#94A3B8',fontWeight:600}}>{s.snagNumber}</span>
                      <span style={{background:sv.bg,color:sv.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>{s.severity}</span>
                      <span style={{background:st.bg,color:st.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>{s.status}</span>
                    </div>
                    <div style={{fontSize:13,fontWeight:600,color:'#0F172A',marginBottom:2}}>{s.title}</div>
                    <div style={{fontSize:12,color:'#64748B'}}>{s.location&&`📍 ${s.location} · `}{s.assignedToName&&`👤 ${s.assignedToName}`}</div>
                    {s.description&&<div style={{fontSize:12,color:'#94A3B8',marginTop:4}}>{s.description}</div>}
                  </div>
                  <div style={{display:'flex',gap:6,flexShrink:0}}>
                    {s.status==='OPEN'&&<TinyBtn onClick={()=>actionMut.mutate({snagId:s.id,action:'START'})} color="#92400E" bg="#FEF3C7">Start</TinyBtn>}
                    {s.status==='IN_PROGRESS'&&<TinyBtn onClick={()=>actionMut.mutate({snagId:s.id,action:'RESOLVE'})} color="#166534" bg="#DCFCE7">Resolve</TinyBtn>}
                    {s.status==='OPEN'&&<TinyBtn onClick={()=>actionMut.mutate({snagId:s.id,action:'REJECT'})} color="#DC2626" bg="#FEF2F2">Reject</TinyBtn>}
                  </div>
                </div>
                {s.photoUrls&&s.photoUrls.length>0&&(
                  <div style={{display:'flex',gap:6,marginTop:8}}>
                    {s.photoUrls.slice(0,4).map((url,i)=>(
                      <a key={i} href={url} target="_blank" rel="noreferrer">
                        <img src={url} alt="" style={{width:48,height:48,objectFit:'cover' as const,borderRadius:6,border:'1px solid #E2E8F0'}} onError={e=>(e.currentTarget.style.display='none')}/>
                      </a>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showAdd&&(
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'#fff',borderRadius:14,padding:28,width:500,boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Log Snag</h3>
              <button onClick={()=>setShowAdd(false)} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',fontSize:20}}>×</button>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Title *</label><input value={form.title} onChange={e=>sf('title',e.target.value)} placeholder="Cracked concrete slab" style={inp} autoFocus/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Severity</label><select value={form.severity} onChange={e=>sf('severity',e.target.value)} style={inp}>{['LOW','MEDIUM','HIGH','CRITICAL'].map(s=><option key={s} value={s}>{s}</option>)}</select></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Location</label><input value={form.location} onChange={e=>sf('location',e.target.value)} placeholder="Block A, Level 2" style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Assigned To</label><input value={form.assignedToName} onChange={e=>sf('assignedToName',e.target.value)} placeholder="Contractor name" style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Due Date</label><input type="date" value={form.dueDate} onChange={e=>sf('dueDate',e.target.value)} style={inp}/></div>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Description</label><textarea value={form.description} onChange={e=>sf('description',e.target.value)} style={{...inp,minHeight:60,resize:'vertical' as const}}/></div>
            </div>
            {err&&<div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowAdd(false)} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer'}}>Cancel</button>
              <button onClick={()=>{if(!form.title.trim()){setErr('Title required');return}addMut.mutate({title:form.title.trim(),description:form.description||null,location:form.location||null,severity:form.severity,assignedTo:null,assignedToName:form.assignedToName||null,dueDate:form.dueDate||null})}} disabled={addMut.isPending} style={{padding:'9px 16px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:addMut.isPending?.6:1}}>{addMut.isPending?'Saving…':'Log Snag'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function DiaryField({label,value,warn}:{label:string;value:string;warn?:boolean}){
  return <div style={{background:warn?'#FEF2F2':'#F8FAFC',borderRadius:8,padding:'8px 10px'}}><div style={{fontSize:10,fontWeight:600,color:warn?'#DC2626':'#94A3B8',textTransform:'uppercase' as const,letterSpacing:'0.04em',marginBottom:3}}>{label}</div><div style={{fontSize:12,color:warn?'#DC2626':'#374151'}}>{value}</div></div>
}
function TinyBtn({onClick,color,bg,children}:any){return<button onClick={onClick} style={{padding:'4px 10px',fontSize:11,fontWeight:700,color,background:bg,border:'none',borderRadius:6,cursor:'pointer'}}>{children}</button>}
function Empty({icon:Icon,text,sub}:{icon:React.ElementType;text:string;sub:string}){return<div style={{textAlign:'center' as const,padding:'50px 20px',color:'#94A3B8'}}><Icon size={36} style={{marginBottom:10,opacity:.3}}/><div style={{fontWeight:600,color:'#475569',marginBottom:4}}>{text}</div><div style={{fontSize:13}}>{sub}</div></div>}
