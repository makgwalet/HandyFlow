// src/pages/projects/tabs/DocumentsTab.tsx
import React, { useState, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, FileText, ExternalLink, Upload, X, Link2 } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface Doc {
  id:string; documentType:string; title:string; revision:string|null; fileUrl:string|null
  fileName:string|null; fileSizeKb:number|null; status:string; description:string|null
  uploadedByName:string|null; createdAt:string
}

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

const MAX_FILE_MB = 10
const ACCEPT = '.pdf,.dwg,.dxf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx,.csv,.zip'
const fmtSize = (bytes:number) => bytes < 1024*1024 ? `${(bytes/1024).toFixed(1)} KB` : `${(bytes/(1024*1024)).toFixed(1)} MB`
const readAsDataUrl = (file:File):Promise<string> =>
  new Promise((res,rej)=>{ const r=new FileReader(); r.onload=()=>res(r.result as string); r.onerror=rej; r.readAsDataURL(file) })

// Drop zone
function DropZone({ file, onFile, onClear }:{ file:File|null; onFile:(f:File)=>void; onClear:()=>void }) {
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleDrop = useCallback((e:React.DragEvent) => {
    e.preventDefault(); setDragging(false)
    const f = e.dataTransfer.files[0]; if (f) onFile(f)
  }, [onFile])

  if (file) {
    const ext = file.name.split('.').pop()?.toUpperCase() ?? 'FILE'
    return (
      <div style={{border:'1.5px solid #DBEAFE',borderRadius:10,padding:'14px 16px',display:'flex',alignItems:'center',gap:12,background:'#F8FAFC'}}>
        <div style={{width:40,height:40,borderRadius:8,background:'#DBEAFE',display:'flex',flexDirection:'column' as const,alignItems:'center',justifyContent:'center',flexShrink:0}}>
          <FileText size={17} color="#1D4ED8"/>
          <span style={{fontSize:8,fontWeight:800,color:'#1D4ED8',marginTop:1}}>{ext}</span>
        </div>
        <div style={{flex:1,minWidth:0}}>
          <div style={{fontSize:13,fontWeight:600,color:'#0F172A',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap' as const}}>{file.name}</div>
          <div style={{fontSize:11,color:'#94A3B8',marginTop:2}}>{fmtSize(file.size)} · Ready to upload</div>
        </div>
        <button onClick={onClear} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',padding:4,borderRadius:6,display:'flex',alignItems:'center'}}>
          <X size={15}/>
        </button>
      </div>
    )
  }

  return (
    <div
      onClick={() => inputRef.current?.click()}
      onDragOver={e=>{ e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      style={{
        border:`2px dashed ${dragging?'#1B3A6B':'#CBD5E1'}`,borderRadius:10,
        padding:'32px 20px',textAlign:'center' as const,cursor:'pointer',
        background:dragging?'#EFF6FF':'#FAFAFA',transition:'all 0.15s',userSelect:'none' as const,
      }}
    >
      <div style={{
        width:48,height:48,borderRadius:'50%',
        background:dragging?'#DBEAFE':'#F1F5F9',
        display:'flex',alignItems:'center',justifyContent:'center',
        margin:'0 auto 12px',transition:'all 0.15s',
      }}>
        <Upload size={22} color={dragging?'#1B3A6B':'#94A3B8'}/>
      </div>
      <div style={{fontSize:14,fontWeight:600,color:dragging?'#1B3A6B':'#374151',marginBottom:4}}>
        {dragging ? 'Drop to upload' : 'Drag & drop or click to browse'}
      </div>
      <div style={{fontSize:12,color:'#94A3B8'}}>PDF, DWG, DXF, JPG, PNG, DOCX · max {MAX_FILE_MB} MB</div>
      <input
        ref={inputRef} type="file" accept={ACCEPT} style={{display:'none'}}
        onChange={e=>{ const f=e.target.files?.[0]; if(f) onFile(f); e.target.value='' }}
      />
    </div>
  )
}

export function DocumentsTab({projectId}:{projectId:string}) {
  const qc = useQueryClient()
  const [typeFilter,setTypeFilter] = useState('')
  const [showUpload,setShowUpload] = useState(false)
  const [err,setErr] = useState('')
  const [uploading,setUploading] = useState(false)
  const [uploadMode,setUploadMode] = useState<'drop'|'url'>('drop')
  const [selectedFile,setSelectedFile] = useState<File|null>(null)
  const initF = () => ({ documentType:'GENERAL', title:'', revision:'', description:'', manualUrl:'' })
  const [form,setForm] = useState(initF())
  const sf = (k:string,v:string) => setForm(p=>({...p,[k]:v}))

  const {data:docs=[],isLoading} = useQuery<Doc[]>({
    queryKey:['pm-docs',projectId,typeFilter],
    queryFn:async()=>{ const url=typeFilter?`/api/v1/projects/${projectId}/documents?type=${typeFilter}`:`/api/v1/projects/${projectId}/documents`; const r=await apiClient.get(url); return unwrap<Doc>(r) },
    staleTime:30_000,
  })

  const statusMut = useMutation({
    mutationFn:({docId,action}:{docId:string;action:string})=>apiClient.post(`/api/v1/projects/documents/${docId}/${action}`),
    onSuccess:()=>qc.invalidateQueries({queryKey:['pm-docs',projectId]}),
  })

  const handleFileSelect = (f:File) => {
    if (f.size > MAX_FILE_MB * 1024 * 1024) {
      setErr(`File exceeds ${MAX_FILE_MB} MB. Use "Paste URL" to link a hosted file.`); return
    }
    setErr(''); setSelectedFile(f)
    if (!form.title) {
      const base = f.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g,' ')
      sf('title', base.charAt(0).toUpperCase() + base.slice(1))
    }
  }

  const handleClose = () => {
    setShowUpload(false); setForm(initF()); setSelectedFile(null); setErr(''); setUploadMode('drop')
  }

  const handleSubmit = async () => {
    setErr('')
    if (!form.title.trim()) { setErr('Title is required'); return }
    if (uploadMode==='drop' && !selectedFile) { setErr('Please select a file'); return }
    if (uploadMode==='url' && !form.manualUrl.trim()) { setErr('Please enter a URL'); return }
    try {
      setUploading(true)
      let fileUrl:string, fileName:string|null=null, fileSizeKb:number|null=null
      if (uploadMode==='drop' && selectedFile) {
        fileUrl = await readAsDataUrl(selectedFile)
        fileName = selectedFile.name
        fileSizeKb = Math.round(selectedFile.size/1024)
      } else {
        fileUrl = form.manualUrl.trim()
        try { fileName = new URL(fileUrl).pathname.split('/').pop()??null } catch {}
      }
      await apiClient.post(`/api/v1/projects/${projectId}/documents`, {
        documentType:form.documentType, title:form.title.trim(), revision:form.revision||null,
        fileUrl, fileName, fileSizeKb, description:form.description||null,
      })
      qc.invalidateQueries({queryKey:['pm-docs',projectId]}); handleClose()
    } catch(e:any) {
      setErr(e.response?.data?.message||'Upload failed. Please try again.')
    } finally {
      setUploading(false)
    }
  }

  const groups = docs.reduce((acc,d)=>{ if(!acc[d.documentType])acc[d.documentType]=[]; acc[d.documentType].push(d); return acc },{} as Record<string,Doc[]>)

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
        <button onClick={()=>{setShowUpload(true);setErr('')}}
          style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Upload Document
        </button>
      </div>

      {isLoading ? <div style={{padding:40,textAlign:'center' as const,color:'#94A3B8'}}>Loading…</div>
        : docs.length===0
          ? <div style={{textAlign:'center' as const,padding:'50px 20px',color:'#94A3B8'}}>
              <FileText size={36} style={{marginBottom:10,opacity:.3}}/>
              <div style={{fontWeight:600,color:'#475569'}}>No documents</div>
              <div style={{fontSize:13}}>Upload drawings, RFIs and contracts to manage your document register</div>
            </div>
          : (typeFilter
              ? <DocList docs={docs} onAction={statusMut.mutate}/>
              : Object.entries(groups).map(([type,items])=>(
                  <div key={type} style={{marginBottom:20}}>
                    <div style={{fontSize:12,fontWeight:700,color:DOC_COLOR[type]?.color??'#475569',textTransform:'uppercase' as const,letterSpacing:'0.05em',marginBottom:8}}>{type} ({items.length})</div>
                    <DocList docs={items} onAction={statusMut.mutate}/>
                  </div>
                ))
            )
      }

      {/* Upload modal */}
      {showUpload && (
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'#fff',borderRadius:14,padding:28,width:540,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>

            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Upload Document</h3>
              <button onClick={handleClose} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',fontSize:20,lineHeight:1}}>×</button>
            </div>

            {/* Metadata */}
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12,marginBottom:16}}>
              <div>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Document Type</label>
                <select value={form.documentType} onChange={e=>sf('documentType',e.target.value)} style={inp}>
                  {DOC_TYPES.map(t=><option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Revision</label>
                <input value={form.revision} onChange={e=>sf('revision',e.target.value)} placeholder="Rev A" style={inp}/>
              </div>
              <div style={{gridColumn:'span 2'}}>
                <label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Title *</label>
                <input value={form.title} onChange={e=>sf('title',e.target.value)} placeholder="Foundation layout drawing" style={inp} autoFocus/>
              </div>
            </div>

            {/* File / URL section */}
            <div style={{marginBottom:16}}>
              <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:10}}>
                <span style={{fontSize:12,fontWeight:600,color:'#374151'}}>File</span>
                <div style={{display:'flex',background:'#F1F5F9',borderRadius:8,padding:2,gap:2}}>
                  {(['drop','url'] as const).map(m=>(
                    <button key={m} onClick={()=>{ setUploadMode(m); setErr('') }}
                      style={{
                        display:'flex',alignItems:'center',gap:4,padding:'4px 10px',borderRadius:6,
                        border:'none',cursor:'pointer',fontSize:11,fontWeight:600,
                        background:uploadMode===m?'#fff':'transparent',
                        color:uploadMode===m?'#1B3A6B':'#64748B',
                        boxShadow:uploadMode===m?'0 1px 3px rgba(0,0,0,0.08)':'none',
                        transition:'all 0.15s',
                      }}>
                      {m==='drop' ? <><Upload size={11}/>&nbsp;Upload</> : <><Link2 size={11}/>&nbsp;Paste URL</>}
                    </button>
                  ))}
                </div>
              </div>

              {uploadMode==='drop'
                ? <DropZone file={selectedFile} onFile={handleFileSelect} onClear={()=>{ setSelectedFile(null); setErr('') }}/>
                : <input value={form.manualUrl} onChange={e=>sf('manualUrl',e.target.value)} placeholder="https://storage.example.com/document.pdf" style={inp}/>
              }
            </div>

            {/* Description */}
            <div style={{marginBottom:4}}>
              <label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Description</label>
              <textarea value={form.description} onChange={e=>sf('description',e.target.value)} placeholder="Optional notes…" style={{...inp,minHeight:56,resize:'vertical' as const}}/>
            </div>

            {err && <div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{err}</div>}

            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={handleClose} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer',color:'#64748B'}}>Cancel</button>
              <button onClick={handleSubmit} disabled={uploading}
                style={{display:'flex',alignItems:'center',gap:6,padding:'9px 18px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:uploading?.6:1}}>
                {uploading
                  ? <><span style={{width:13,height:13,border:'2px solid rgba(255,255,255,0.35)',borderTopColor:'#fff',borderRadius:'50%',display:'inline-block',animation:'spin 0.7s linear infinite'}}/> Uploading…</>
                  : <><Upload size={13}/> Upload</>
                }
              </button>
            </div>
          </div>
        </div>
      )}
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  )
}

function DocList({docs,onAction}:{docs:Doc[];onAction:(a:any)=>void}) {
  return (
    <div style={{border:'1px solid #E2E8F0',borderRadius:10,overflow:'hidden'}}>
      {docs.map((d,i)=>{
        const dc = DOC_COLOR[d.documentType]??{bg:'#F1F5F9',color:'#475569'}
        const sc = STATUS_COLOR[d.status]??{bg:'#F1F5F9',color:'#64748B'}
        const ext = d.fileName?.split('.').pop()?.toUpperCase()??null
        return (
          <div key={d.id} style={{display:'flex',alignItems:'center',gap:12,padding:'11px 14px',borderTop:i>0?'1px solid #F1F5F9':'none',background:i%2===0?'#fff':'#FAFAFA'}}>
            <div style={{width:34,height:34,borderRadius:7,background:dc.bg,display:'flex',flexDirection:'column' as const,alignItems:'center',justifyContent:'center',flexShrink:0}}>
              <FileText size={13} color={dc.color}/>
              {ext&&<span style={{fontSize:7,fontWeight:800,color:dc.color,marginTop:1,lineHeight:1}}>{ext}</span>}
            </div>
            <div style={{flex:1,minWidth:0}}>
              <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:2}}>
                <span style={{fontSize:13,fontWeight:600,color:'#0F172A',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap' as const}}>{d.title}</span>
                {d.revision&&<span style={{fontSize:10,background:'#F1F5F9',color:'#475569',padding:'1px 6px',borderRadius:12,fontWeight:600,flexShrink:0}}>{d.revision}</span>}
                <span style={{background:sc.bg,color:sc.color,fontSize:10,fontWeight:700,padding:'1px 6px',borderRadius:12,flexShrink:0}}>{d.status}</span>
              </div>
              <div style={{fontSize:11,color:'#94A3B8'}}>
                {d.uploadedByName&&<span>{d.uploadedByName} · </span>}
                {new Date(d.createdAt).toLocaleDateString('en-ZA')}
                {d.fileSizeKb&&<span> · {d.fileSizeKb<1024?`${d.fileSizeKb} KB`:`${(d.fileSizeKb/1024).toFixed(1)} MB`}</span>}
              </div>
            </div>
            <div style={{display:'flex',gap:6,flexShrink:0,alignItems:'center'}}>
              {d.fileUrl&&!d.fileUrl.startsWith('data:')&&(
                <a href={d.fileUrl} target="_blank" rel="noreferrer"
                  style={{display:'flex',alignItems:'center',gap:4,fontSize:11,color:'#1D4ED8',fontWeight:600,textDecoration:'none',padding:'4px 8px',borderRadius:6,background:'#EFF6FF'}}>
                  <ExternalLink size={11}/> Open
                </a>
              )}
              {d.fileUrl&&d.fileUrl.startsWith('data:')&&(
                <a href={d.fileUrl} download={d.fileName??'document'}
                  style={{display:'flex',alignItems:'center',gap:4,fontSize:11,color:'#7C3AED',fontWeight:600,textDecoration:'none',padding:'4px 8px',borderRadius:6,background:'#EDE9FE'}}>
                  ↓ Download
                </a>
              )}
              {d.status==='FOR_REVIEW'&&<button onClick={()=>onAction({docId:d.id,action:'APPROVE'})} style={{fontSize:11,padding:'4px 8px',background:'#DCFCE7',color:'#166534',border:'none',borderRadius:6,cursor:'pointer',fontWeight:600}}>Approve</button>}
              {d.status==='CURRENT'&&<button onClick={()=>onAction({docId:d.id,action:'SUBMIT_REVIEW'})} style={{fontSize:11,padding:'4px 8px',background:'#FEF3C7',color:'#92400E',border:'none',borderRadius:6,cursor:'pointer',fontWeight:600}}>Review</button>}
            </div>
          </div>
        )
      })}
    </div>
  )
}
