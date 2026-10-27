// src/pages/facilities/AssetQr.tsx
//
// QR labels and scanning. A label encodes the asset in a short code ("HF:ASSET:…"); scanning it (or typing the code or
// the asset tag) shows the asset with its open jobs, open actions, last inspection and next planned maintenance.
// Scanning uses the phone camera where the browser supports it (Chrome, Edge, Android); otherwise type the code.
import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Printer, ScanLine } from 'lucide-react'
import QRCode from 'qrcode'
import { facilitiesApi, type Asset, type AssetLookup } from '../../api/facilities.api'
import { errorMessage } from '../../store/toast.store'
import { Btn, DefList, Dialog, Field, Notice, PriorityChip, StatusChip } from './ui'
import { fmtDate, inputStyle, titleCase } from './format'

/** The printable label: QR code, tag and name. */
export function AssetQrDialog({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const payload = useQuery({ queryKey: ['facilities', 'asset-qr', asset.id], queryFn: () => facilitiesApi.assetQr(asset.id) })
  const [img, setImg] = useState<string | null>(null)
  useEffect(() => {
    if (!payload.data) return
    let live = true
    QRCode.toDataURL(payload.data, { width: 320, margin: 2, errorCorrectionLevel: 'M' }).then(u => { if (live) setImg(u) })
    return () => { live = false }
  }, [payload.data])
  const print = () => {
    if (!img) return
    const w = window.open('', '_blank', 'width=420,height=560')
    if (!w) return
    const esc = (s: string) => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c] as string)
    w.document.write(`<!doctype html><title>${esc(asset.assetTag)}</title><body style="font-family:sans-serif;text-align:center;margin:24px">
      <img src="${img}" style="width:260px;height:260px"><div style="font-size:22px;font-weight:700;margin-top:8px">${esc(asset.assetTag)}</div>
      <div style="font-size:14px">${esc(asset.name)}</div><script>onload=()=>{print()}</script></body>`)
    w.document.close()
  }
  return (
    <Dialog title={`QR label · ${asset.assetTag}`} onClose={onClose} width={400}
      footer={<><Btn onClick={onClose}>Close</Btn><Btn kind="primary" disabled={!img} onClick={print}><Printer size={14} />Print label</Btn></>}>
      <div style={{ textAlign: 'center' }}>
        {payload.isError && <Notice tone="bad">The code could not be loaded.</Notice>}
        {img ? <img src={img} alt={`QR code for ${asset.assetTag}`} width={260} height={260} style={{ background: '#fff', borderRadius: 8 }} /> : !payload.isError && <div aria-busy="true" style={{ color: 'var(--hf-text-muted)' }}>Preparing…</div>}
        <div style={{ fontWeight: 700, marginTop: 8 }}>{asset.assetTag}</div>
        <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>{asset.name}</div>
        <p style={{ fontSize: 12, color: 'var(--hf-text-muted)', margin: '12px 0 0' }}>Stick it on the asset. Anyone with access can scan it to see the asset's jobs and history.</p>
      </div>
    </Dialog>
  )
}

interface DetectorLike { detect: (src: CanvasImageSource) => Promise<{ rawValue: string }[]> }
type DetectorCtor = new (o: { formats: string[] }) => DetectorLike

/** Scan or type a code, then show what the server knows about that asset. */
export function AssetScanDialog({ onClose }: { onClose: () => void }) {
  const [code, setCode] = useState('')
  const [scanning, setScanning] = useState(false)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const video = useRef<HTMLVideoElement>(null)
  const Detector = (window as unknown as { BarcodeDetector?: DetectorCtor }).BarcodeDetector
  const lookup = useMutation({ mutationFn: (c: string) => facilitiesApi.assetLookup(c.trim()) })

  useEffect(() => {
    if (!scanning || !Detector) return
    let stream: MediaStream | null = null, stop = false
    const detector = new Detector({ formats: ['qr_code'] })
    ;(async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        if (video.current) { video.current.srcObject = stream; await video.current.play() }
        while (!stop && video.current) {
          const found = await detector.detect(video.current).catch(() => [])
          if (found[0]?.rawValue) { setCode(found[0].rawValue); setScanning(false); lookup.mutate(found[0].rawValue); return }
          await new Promise(r => setTimeout(r, 300))
        }
      } catch {
        setCameraError('The camera could not be opened. Allow camera access, or type the code instead.'); setScanning(false)
      }
    })()
    return () => { stop = true; stream?.getTracks().forEach(t => t.stop()) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scanning])

  const r: AssetLookup | undefined = lookup.data
  return (
    <Dialog title="Find an asset" onClose={onClose} width={520}>
      <form onSubmit={e => { e.preventDefault(); if (code.trim()) lookup.mutate(code) }}>
        <Field label="Scan the QR label, or type the code or asset tag">
          <div style={{ display: 'flex', gap: 8 }}>
            <input value={code} onChange={e => setCode(e.target.value)} placeholder="HF:ASSET:… or AHU-01" style={inputStyle} autoFocus />
            <Btn kind="primary" type="submit" busy={lookup.isPending} disabled={!code.trim()}>Look up</Btn>
          </div>
        </Field>
        {Detector
          ? <Btn onClick={() => { setCameraError(null); setScanning(s => !s) }}><ScanLine size={14} />{scanning ? 'Stop camera' : 'Scan with camera'}</Btn>
          : <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>This browser can't scan from the camera. Use your phone's camera app or type the code.</div>}
      </form>
      {scanning && <video ref={video} muted playsInline style={{ width: '100%', borderRadius: 10, marginTop: 12, background: '#000' }} aria-label="Camera view" />}
      {cameraError && <div style={{ marginTop: 12 }}><Notice tone="warn">{cameraError}</Notice></div>}
      {lookup.isError && <div style={{ marginTop: 12 }}><Notice tone="bad">{errorMessage(lookup.error, 'No asset matched that code, or it is outside your sites.')}</Notice></div>}
      {r && (
        <div style={{ marginTop: 18 }}>
          <h3 style={{ margin: '0 0 8px', fontSize: 14 }}>{r.asset.assetTag} · {r.asset.name}</h3>
          <DefList items={[
            ['Type', titleCase(r.asset.assetType)], ['Location', r.asset.location ?? '—'], ['Status', <StatusChip key="s" status={r.asset.status} />],
            ['Open corrective actions', r.openCorrectiveActions],
            ['Last inspection', r.lastInspection ? `${r.lastInspection.templateName} · ${titleCase(r.lastInspection.result)} · ${fmtDate(r.lastInspection.completedAt)}` : 'None yet'],
            ['Next planned maintenance', r.nextPpmDue ? fmtDate(r.nextPpmDue) : 'None scheduled'],
          ]} />
          <h4 style={{ margin: '16px 0 6px', fontSize: 13 }}>Open work orders ({r.openWorkOrders.length})</h4>
          {r.openWorkOrders.length === 0 && <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>None.</div>}
          {r.openWorkOrders.map(w => (
            <div key={w.id} style={{ padding: '7px 0', borderTop: '1px solid var(--hf-border-subtle)', display: 'flex', gap: 8, alignItems: 'center' }}>
              <div style={{ flex: 1, minWidth: 0 }}><strong>{w.workOrderNumber}</strong> <span style={{ fontSize: 12.5 }}>{w.description}</span></div>
              <PriorityChip priority={w.priority} /><StatusChip status={w.status} />
            </div>
          ))}
        </div>
      )}
    </Dialog>
  )
}
