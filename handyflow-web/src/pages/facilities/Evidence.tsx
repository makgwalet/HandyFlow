// src/pages/facilities/Evidence.tsx
//
// Photos and documents attached to a record (work order, asset, inspection, licence). Lists what is there, lets the
// person download a file, and, when allowed, attach another. The server decides who may attach and what file types and
// sizes it accepts; its message is shown when it refuses.
import { useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileText, Image as ImageIcon, Paperclip } from 'lucide-react'
import { facilitiesApi, type Evidence } from '../../api/facilities.api'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn } from './ui'
import { fmtBytes, fmtDateTime, saveBlob } from './format'

export function EvidencePanel({ queryKey, load, attach, canAttach, accept = 'image/*,application/pdf', note }:
  { queryKey: unknown[]; load: () => Promise<Evidence[]>; attach: (file: File) => Promise<Evidence>; canAttach: boolean; accept?: string; note?: string }) {
  const qc = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const list = useQuery({ queryKey: ['facilities', 'evidence', ...queryKey], queryFn: load })
  const upload = useMutation({
    mutationFn: attach,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities', 'evidence', ...queryKey] }); toast.success('File attached') },
    onError: e => toast.error(errorMessage(e)),
  })
  const download = useMutation({
    mutationFn: async (ev: Evidence) => saveBlob(await facilitiesApi.downloadEvidence(ev.id), ev.fileName),
    onError: e => toast.error(errorMessage(e)),
  })
  const files = list.data ?? []
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '22px 0 8px' }}>
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 700 }}>Photos and documents{files.length ? ` (${files.length})` : ''}</h3>
        {canAttach && (
          <>
            <input ref={input} type="file" accept={accept} hidden onChange={e => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = '' }} />
            <Btn busy={upload.isPending} onClick={() => input.current?.click()}><Paperclip size={14} />Attach</Btn>
          </>
        )}
      </div>
      {note && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 8 }}>{note}</div>}
      {list.isLoading && <div aria-busy="true" style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>Loading…</div>}
      {list.isError && <div role="alert" style={{ fontSize: 12.5, color: 'var(--hf-danger-text)' }}>The files could not be loaded.</div>}
      {!list.isLoading && !list.isError && files.length === 0 && <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>Nothing attached yet.</div>}
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {files.map(f => (
          <li key={f.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
            <span style={{ color: 'var(--hf-text-faint)', display: 'flex' }}>{f.contentType.startsWith('image/') ? <ImageIcon size={16} /> : <FileText size={16} />}</span>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.fileName}</div>
              <div style={{ fontSize: 11.5, color: 'var(--hf-text-muted)' }}>{fmtBytes(f.fileSizeBytes)} · {f.uploadedByName ?? 'Unknown'} · {fmtDateTime(f.createdAt)}</div>
            </div>
            <Btn aria-label={`Download ${f.fileName}`} busy={download.isPending && download.variables?.id === f.id} onClick={() => download.mutate(f)}><Download size={14} /></Btn>
          </li>
        ))}
      </ul>
    </div>
  )
}
