// src/pages/projects/tabs/DocumentsTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, FileText, FileImage, File, ExternalLink } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface Doc { id:string; documentType:string; title:string; revision:string|null; fileUrl:string|null; fileName:string|null; fileSizeKb:number|null; status:string; description:string|null; uploadedByName:string|null; createdAt:string }

function unwrap<T>(r:any):T[]{const d=r?.data?.data??r?.data??[];return Array.isArray(d)?d as T[]:d?.content??[]}
const inp:React.CSSProperties={width:'100%',padding:'9px 12px',border:'1.5px solid #E2E8F0',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'#fff'}
const DOC_TYPES=['DRAWING','RFI','SUBMITTAL','CONTRACT','REPORT','PHOTO','GENERAL']
const DOC_COLOR:Record<string,{bg:string;color:string}>={
  DRAWING:{bg:'#DBEAFE',color:'#1D4ED8'},RFI:{bg:'#FEF3C7',color:'#92400E'},
  SUBMITTAL:{bg:'#EDE9FE',color:'#7C3AED'},CONTRACT:{bg:'#DCFCE7',color:'#166534'},
  REPORT:{bg:'#F1F5F9',color:'#475569'},PHOTO:{bg:'#FEF2F2',color:'#DC2626'},GENERAL:{bg:'#F1F5F9',color:'#64748B'},
}
const STATUS_COLOR:Record<string,{bg:string;color:string}>={
  CURRENT:{bg:'#DCFCE7',color:'#166534'},APPROVED:{bg:'#DBEAFE',color:'#1D4ED8'},
  FOR_REVIEW:{bg:'#FEF3C7',color:'#92400E'},DRAFT:{bg:'#F1F5F9',color:'#475569'},SUPERSEDED:{bg:'#F1F5F9',color:'#9CA3AF'},
}

export function DocumentsTab({projectId}:{projectId:string}) {
  const qc=useQueryClient()
  const [typeFilter,setTypeFilter]=useState('')
  const [showUpload,setShowUpload]=useState(false)
  const [err,setErr]=useState('')
  const initF=()=>({documentType:'GENERAL',title:'',revision:'',fileUrl:'',fileName:'',description:''})
  const [form,setForm]=useState(initF())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:docs=[],isLoading}=useQuery<Doc[]>({
    queryKey:['pm-docs',projectId,typeFilter],
    queryFn:async()=>{const url=typeFilter?`/api/v1/projects/${projectId}/documents?type=${typeFilter}`:`/api/v1/projects/${projectId}/documents`;const r=await apiClient.get(url);return unwrap<Doc>(r)},
    staleTime:30_000,
  })

  const uploadMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/documents`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-docs',projectId]});setShowUpload(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to upload document'),
  })
  const statusMut=useMutation({
    mutationFn:({docId,action}:{docId:string;action:string})=>apiClient.post(`/api/v1/projects/documents/${docId}/${action}`),
    onSuccess:()=>qc.invalidateQueries({queryKey:['pm-docs',projectId]}),
  })

  const groups=docs.reduce((acc,d)=>{if(!acc[d.documentType])acc[d.documentType]=[];acc[d.documentType].push(d);return acc},{} as Record<string,Doc[]>)

  return (
    <div>
      {/* Type filter */}
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,flexWrap:'wrap' as const,gap:10}}>
        <div style={{display:'flex',gap:6,flexWrap:'wrap' as const}}>
          {['','DRAWING','RFI','SUBMITTAL','CONTRACT','REPORT','PHOTO','GENERAL'].map(t=>(
            <button key={t} onClick={()=>setTypeFilter(t)}
              style={{padding:'5px 12px',borderRadius:20,border:typeFilter===t?'1.5px solid #1B3A6B':'1px solid #E2E8F0',background:typeFilter===t?'#EFF6FF':'#fff',color:typeFilter===t?'#1B3A6B':'#64748B',fontSize:12,fontWeight:typeFilter===t?700:400,cursor:'pointer'}}>
              {t||'All'}
            </button>
          ))}
        </div>
        <button onClick={()=>{setShowUpload(true);setErr('')}} style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Upload Document
        </button>
      </div>

      {isLoading?<div style={{padding:40,textAlign:'center',color:'#94A3B8'}}>Loading…</div>
        :docs.length===0?<div style={{textAlign:'center',padding:'50px 20px',color:'#94A3B8'}}><FileText size={36} style={{marginBottom:10,opacity:.3}}/><div style={{fontWeight:600,color:'#475569'}}>No documents</div><div style={{fontSize:13}}>Upload drawings, RFIs and contracts to manage your document register</div></div>
        :(typeFilter?(<DocList docs={docs} onAction={statusMut.mutate}/>)
          :Object.entries(groups).map(([type,items])=>(
            <div key={type} style={{marginBottom:20}}>
              <div style={{fontSize:12,fontWeight:700,color:DOC_COLOR[type]?.color??'#475569',textTransform:'uppercase' as const,letterSpacing:'0.05em',marginBottom:8}}>{type} ({items.length})</div>
              <DocList docs={items} onAction={statusMut.mutate}/>
            </div>
          ))
        )
      }

      {showUpload&&(
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'#fff',borderRadius:14,padding:28,width:520,boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Upload Document</h3>
              <button onClick={()=>setShowUpload(false)} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',fontSize:20}}>×</button>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Document Type</label><select value={form.documentType} onChange={e=>sf('documentType',e.target.value)} style={inp}>{DOC_TYPES.map(t=><option key={t} value={t}>{t}</option>)}</select></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Revision</label><input value={form.revision} onChange={e=>sf('revision',e.target.value)} placeholder="Rev A" style={inp}/></div>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Title *</label><input value={form.title} onChange={e=>sf('title',e.target.value)} placeholder="Foundation layout drawing" style={inp} autoFocus/></div>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>File URL *</label><input value={form.fileUrl} onChange={e=>sf('fileUrl',e.target.value)} placeholder="https://storage.example.com/doc.pdf" style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>File Name</label><input value={form.fileName} onChange={e=>sf('fileName',e.target.value)} placeholder="foundation-rev-a.pdf" style={inp}/></div>
              <div style={{gridColumn:'span 2'}}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Description</label><textarea value={form.description} onChange={e=>sf('description',e.target.value)} style={{...inp,minHeight:50,resize:'vertical' as const}}/></div>
            </div>
            {err&&<div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowUpload(false)} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer'}}>Cancel</button>
              <button onClick={()=>{if(!form.title.trim()||!form.fileUrl.trim()){setErr('Title and URL required');return}uploadMut.mutate({documentType:form.documentType,title:form.title.trim(),revision:form.revision||null,fileUrl:form.fileUrl.trim(),fileName:form.fileName||null,description:form.description||null})}} disabled={uploadMut.isPending} style={{padding:'9px 16px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:uploadMut.isPending?.6:1}}>{uploadMut.isPending?'Uploading…':'Upload'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function DocList({docs,onAction}:{docs:Doc[];onAction:(a:any)=>void}) {
  return (
    <div style={{border:'1px solid #E2E8F0',borderRadius:10,overflow:'hidden'}}>
      {docs.map((d,i)=>{
        const dc=DOC_COLOR[d.documentType]??{bg:'#F1F5F9',color:'#475569'}
        const sc=STATUS_COLOR[d.status]??{bg:'#F1F5F9',color:'#64748B'}
        return (
          <div key={d.id} style={{display:'flex',alignItems:'center',gap:12,padding:'11px 14px',borderTop:i>0?'1px solid #F1F5F9':'none',background:i%2===0?'#fff':'#FAFAFA'}}>
            <FileText size={18} color={dc.color} style={{flexShrink:0}}/>
            <div style={{flex:1,minWidth:0}}>
              <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:2}}>
                <span style={{fontSize:13,fontWeight:600,color:'#0F172A',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap' as const}}>{d.title}</span>
                {d.revision&&<span style={{fontSize:10,background:'#F1F5F9',color:'#475569',padding:'1px 6px',borderRadius:12,fontWeight:600,flexShrink:0}}>{d.revision}</span>}
                <span style={{background:sc.bg,color:sc.color,fontSize:10,fontWeight:700,padding:'1px 6px',borderRadius:12,flexShrink:0}}>{d.status}</span>
              </div>
              <div style={{fontSize:11,color:'#94A3B8'}}>{d.uploadedByName} · {new Date(d.createdAt).toLocaleDateString('en-ZA')}</div>
            </div>
            <div style={{display:'flex',gap:6,flexShrink:0}}>
              {d.fileUrl&&<a href={d.fileUrl} target="_blank" rel="noreferrer" style={{display:'flex',alignItems:'center',gap:4,fontSize:11,color:'#1D4ED8',fontWeight:600,textDecoration:'none'}}><ExternalLink size={12}/>Open</a>}
              {d.status==='FOR_REVIEW'&&<button onClick={()=>onAction({docId:d.id,action:'APPROVE'})} style={{fontSize:11,padding:'3px 8px',background:'#DCFCE7',color:'#166534',border:'none',borderRadius:6,cursor:'pointer',fontWeight:600}}>Approve</button>}
              {d.status==='CURRENT'&&<button onClick={()=>onAction({docId:d.id,action:'SUBMIT_REVIEW'})} style={{fontSize:11,padding:'3px 8px',background:'#FEF3C7',color:'#92400E',border:'none',borderRadius:6,cursor:'pointer',fontWeight:600}}>Review</button>}
            </div>
          </div>
        )
      })}
    </div>
  )
}
