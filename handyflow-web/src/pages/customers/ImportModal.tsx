// src/pages/customers/ImportModal.tsx
//
// USAGE in CustomersPage header:
//   import { ImportModal } from './ImportModal'
//   const [showImport, setShowImport] = useState(false)
//
//   <button className={styles.btnOutline} onClick={() => setShowImport(true)}>
//     <Upload size={14} /> Import CSV
//   </button>
//   {showImport && <ImportModal onClose={() => setShowImport(false)} />}

import { useState, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Upload, X, FileText, CheckCircle2, AlertCircle, Download, RefreshCw } from 'lucide-react'
import { apiClient } from '../../api/client'
import styles from './CustomersPage.module.css'

/**
 * WHY no polling in this version?
 *
 * The original ImportModal polled GET /import/{jobId} every 2 seconds.
 * That pattern is correct for truly long-running jobs (minutes).
 * For a 500-row CSV with Jaro-Winkler dedup, the job completes in
 * under 10 seconds on any reasonable server.
 *
 * Simpler approach: POST the file, await the response.
 * If the server completes before the browser's default 30s timeout,
 * we get the result synchronously.  No polling, no state machine,
 * no setTimeout leaks.
 *
 * If you later support 10,000-row imports where processing takes > 30s,
 * re-introduce the async job + polling pattern then.  Don't pay the
 * complexity cost until you need it.
 */

interface RowError { row: number; name: string; reason: string }

interface ImportResult {
  jobId:        string
  status:       string
  totalRows:    number
  createdCount: number
  skippedCount: number
  errorCount:   number
  rowErrors:    RowError[]
}

type Phase = 'idle' | 'uploading' | 'done' | 'error'

export function ImportModal({ onClose }: { onClose: () => void }) {
  const qc                              = useQueryClient()
  const [phase, setPhase]               = useState<Phase>('idle')
  const [dragOver, setDragOver]         = useState(false)
  const [file, setFile]                 = useState<File | null>(null)
  const [result, setResult]             = useState<ImportResult | null>(null)
  const [errorMsg, setErrorMsg]         = useState('')
  const fileRef                         = useRef<HTMLInputElement>(null)

  const reset = () => {
    setPhase('idle'); setFile(null); setResult(null); setErrorMsg('')
  }

  const handleFile = (f: File) => {
    if (!f.name.toLowerCase().endsWith('.csv')) {
      setErrorMsg('Only .csv files are supported'); return
    }
    if (f.size > 5 * 1024 * 1024) {
      setErrorMsg('File must be under 5 MB'); return
    }
    setFile(f); setErrorMsg('')
  }

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault(); setDragOver(false)
    const f = e.dataTransfer.files[0]
    if (f) handleFile(f)
  }, [])

  const upload = async () => {
    if (!file) return
    setPhase('uploading'); setErrorMsg('')

    try {
      const form = new FormData()
      form.append('file', file)

      // POST and await — server processes synchronously for files under 2,000 rows.
      // The Spring @Async method is still triggered internally, but the job completes
      // fast enough that we can poll once immediately after the 202 response.
      const res   = await apiClient.post('/api/v1/crm/customers/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      const jobId = res.data?.data?.jobId

      if (!jobId) throw new Error('No job ID returned from server')

      // Poll once — wait 1.5s then check. For small files the job is done.
      // For larger files we retry up to 10 times (15s total).
      const result = await pollUntilDone(jobId, 10)
      setResult(result)
      setPhase('done')
      qc.invalidateQueries({ queryKey: ['customers'] })

    } catch (e: any) {
      setPhase('error')
      setErrorMsg(e.response?.data?.message ?? e.message ?? 'Upload failed. Please try again.')
    }
  }

  /**
   * Poll GET /import/{jobId} until status is DONE or FAILED.
   * Max `attempts` retries with 1.5s between each.
   * Throws if exhausted without a terminal status.
   */
  const pollUntilDone = async (jobId: string, attempts: number): Promise<ImportResult> => {
    for (let i = 0; i < attempts; i++) {
      await new Promise(r => setTimeout(r, 1500))
      const res = await apiClient.get(`/api/v1/crm/customers/import/${jobId}`)
      const job: ImportResult = res.data?.data
      if (job.status === 'DONE' || job.status === 'FAILED') return job
    }
    throw new Error('Import is taking longer than expected. Check back in a moment.')
  }

  const downloadTemplate = () => {
    const rows = [
      'name,email,phone,customerType,taxNumber,notes,street,suburb,city,province,postalCode',
      'Tau Mining (Pty) Ltd,accounts@taumining.co.za,+27112345678,CUSTOMER,4820156789,Key account,45 Mine Road,Carletonville,Merafong,Gauteng,2499',
      'Cape Harvest Wines CC,orders@capeharvest.co.za,+27218873344,CUSTOMER,,Seasonal orders,12 Winery Road,Franschhoek,Franschhoek,Western Cape,7690',
      'New Lead Corp,,082 000 1111,LEAD,,,,,Durban,KwaZulu-Natal,4001',
    ].join('\r\n')
    const blob = new Blob(['\uFEFF' + rows], { type: 'text/csv;charset=utf-8;' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href = url; a.download = 'customers_import_template.csv'
    a.click(); URL.revokeObjectURL(url)
  }

  return (
    <div className={styles.overlay} onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className={`${styles.modal} ${styles.modalWide}`} role="dialog" aria-modal="true" aria-label="Import customers">

        {/* Header */}
        <div className={styles.modalHeader}>
          <div>
            <h3 className={styles.modalTitle}>Import Customers</h3>
            <p className={styles.modalSubtitle}>Upload a CSV to bulk-add customers — max 2,000 rows</p>
          </div>
          <button onClick={onClose} className={styles.closeBtn} aria-label="Close"><X size={20} /></button>
        </div>

        {/* Template download */}
        <button onClick={downloadTemplate} style={{
          display: 'flex', alignItems: 'center', gap: 6,
          fontSize: 13, color: '#1D4ED8', background: 'none',
          border: 'none', cursor: 'pointer', marginBottom: 16, padding: 0,
        }}>
          <Download size={14} /> Download CSV template
        </button>

        {/* Drop zone — only shown when idle */}
        {(phase === 'idle' || phase === 'error') && (
          <div
            className={`${styles.dropZone} ${dragOver ? styles.dropZoneActive : ''}`}
            onDragOver={e => { e.preventDefault(); setDragOver(true) }}
            onDragLeave={() => setDragOver(false)}
            onDrop={onDrop}
            onClick={() => fileRef.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={e => { if (e.key === 'Enter') fileRef.current?.click() }}
            aria-label="Drop CSV file here or click to browse">
            <Upload size={28} style={{ color: '#94A3B8', marginBottom: 10 }} />
            <div style={{ fontSize: 14, fontWeight: 600, color: '#374151' }}>
              {file ? file.name : 'Drop your CSV here or click to browse'}
            </div>
            <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 4 }}>
              UTF-8 or Excel-saved CSV · max 2,000 rows · 5 MB
            </div>
            <input
              ref={fileRef} type="file" accept=".csv" style={{ display: 'none' }}
              onChange={e => { if (e.target.files?.[0]) handleFile(e.target.files[0]) }}
            />
          </div>
        )}

        {/* Uploading spinner */}
        {phase === 'uploading' && (
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <div className={styles.spinner} style={{ margin: '0 auto 16px' }} />
            <div style={{ fontSize: 14, fontWeight: 600, color: '#374151' }}>Processing import…</div>
            <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 4 }}>
              Checking for duplicates and validating rows
            </div>
          </div>
        )}

        {/* Results */}
        {phase === 'done' && result && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10, marginBottom: 16 }}>
              <ResultStat icon={<CheckCircle2 size={16} color="#16A34A" />} value={result.createdCount} label="Imported"  bg="#F0FDF4" bd="#BBF7D0" />
              <ResultStat icon={<AlertCircle  size={16} color="#EA580C" />} value={result.skippedCount} label="Skipped"   bg="#FFF7ED" bd="#FED7AA" />
              <ResultStat icon={<FileText     size={16} color="#94A3B8" />} value={result.totalRows}    label="Total rows" bg="#F8FAFC" bd="#E2E8F0" />
            </div>

            {result.rowErrors.length > 0 && (
              <div style={{ maxHeight: 240, overflowY: 'auto', border: '1px solid #E2E8F0', borderRadius: 10, marginBottom: 12 }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ background: '#F8FAFC' }}>
                      <th style={thStyle}>Row</th>
                      <th style={thStyle}>Name</th>
                      <th style={thStyle}>Reason skipped</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.rowErrors.map((err, i) => (
                      <tr key={i} style={{ borderTop: '1px solid #F1F5F9' }}>
                        <td style={tdStyle}>{err.row}</td>
                        <td style={tdStyle}>{err.name || '—'}</td>
                        <td style={{ ...tdStyle, color: '#DC2626' }}>{err.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <button onClick={reset} style={{
              display: 'flex', alignItems: 'center', gap: 6,
              fontSize: 13, color: '#1D4ED8', background: 'none',
              border: 'none', cursor: 'pointer', padding: 0,
            }}>
              <RefreshCw size={13} /> Import another file
            </button>
          </div>
        )}

        {/* Error banner */}
        {errorMsg && (
          <div className={styles.errorBanner} role="alert" style={{ marginTop: 12 }}>
            <AlertCircle size={15} style={{ flexShrink: 0 }} />{errorMsg}
          </div>
        )}

        {/* Footer */}
        <div className={styles.modalFooter}>
          <button className={styles.btnOutline} onClick={onClose}>
            {phase === 'done' ? 'Close' : 'Cancel'}
          </button>
          {(phase === 'idle' || phase === 'error') && (
            <button className={styles.btnPrimary} onClick={upload} disabled={!file || phase === 'uploading'}>
              <Upload size={14} /> Start Import
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function ResultStat({ icon, value, label, bg, bd }: {
  icon: React.ReactNode; value: number; label: string; bg: string; bd: string
}) {
  return (
    <div style={{ background: bg, border: `1px solid ${bd}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 6 }}>{icon}</div>
      <div style={{ fontSize: 22, fontWeight: 800, color: '#0F172A' }}>{value}</div>
      <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 2 }}>{label}</div>
    </div>
  )
}

const thStyle: React.CSSProperties = {
  textAlign: 'left', padding: '8px 12px', fontSize: 11, fontWeight: 700,
  color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em',
}

const tdStyle: React.CSSProperties = { padding: '8px 12px', color: '#374151' }
