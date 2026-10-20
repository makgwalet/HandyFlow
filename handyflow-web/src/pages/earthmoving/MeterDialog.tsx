// src/pages/earthmoving/MeterDialog.tsx
//
// One machine's meter: every reading and where it came from, a way to correct a reading that was entered wrongly
// (a reason is required and kept), disputing a kilometre reading that looks wrong (charges built on it are held back
// until it is confirmed or corrected), and, for administrators, switching a machine between hours and kilometres.
import { useState } from "react"
import { Overlay, ModalHead, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { fmtDateTime, apiMessage, meterUnitOf, meterWordOf } from "./shared/format"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useMeterReadings, useCorrectReading, useChangeMeter, useAssignableUsers, useDisputeReading, useConfirmDispute } from "./shared/hooks"
import type { Asset, MeterReading } from "./shared/types"

const SOURCE_LABEL: Record<string, string> = {
  MANUAL: "Entered by hand", SHIFT: "End of shift", SERVICE: "Service record", CORRECTION: "Correction", TYPE_CHANGE: "Meter changed", FIELD: "Sent from a phone",
}
const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})

const DISPUTE_LABEL: Record<string, string> = { OPEN: "Disputed", CONFIRMED: "Dispute closed: reading confirmed", CORRECTED: "Dispute closed: reading corrected" }

/** What a correction, dispute or confirmation did to the machine's kilometre charges, in a sentence; empty when nothing. */
function chargesNote(r: MeterReading): string {
  const plural = (n: number, w: string) => `${n} ${w}${n === 1 ? "" : "s"}`
  const parts: string[] = []
  if (r.chargesRecalculated) parts.push(`${plural(r.chargesRecalculated, "hire charge")} recalculated`)
  if (r.chargesHeld) parts.push(`${plural(r.chargesHeld, "hire charge")} held back from invoicing`)
  if (r.chargesInvoiced) parts.push(`${plural(r.chargesInvoiced, "invoiced charge")} no longer match this reading, so review them in Invoicing`)
  return parts.join("; ")
}

export default function MeterDialog({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const perms = useEarthPerms()
  const unit = meterUnitOf(asset)
  const word = meterWordOf(asset)
  const otherType = asset.meterType === "KILOMETRES" ? "HOURS" : "KILOMETRES"
  const otherWord = otherType === "KILOMETRES" ? "kilometres" : "hours"
  const [mode, setMode] = useState<"history" | "correct" | "change" | "dispute" | "confirm">("history")
  const [target, setTarget] = useState<MeterReading | null>(null)
  const [text, setText] = useState("")
  const [notice, setNotice] = useState("")
  const [err, setErr] = useState("")
  const [corr, setCorr] = useState({ reading: "", reason: "" })
  const [chg, setChg] = useState({ current: "", lastService: "", interval: "", reason: "" })

  const { data: rows = [], isLoading } = useMeterReadings(asset.id)
  const { data: users = [] } = useAssignableUsers()
  const back = () => { setMode("history"); setErr(""); setTarget(null); setText("") }
  const afterReading = (r: MeterReading) => { back(); setNotice(chargesNote(r)) }
  const correct = useCorrectReading(afterReading)
  const change = useChangeMeter(back)
  const dispute = useDisputeReading(afterReading)
  const confirm = useConfirmDispute(afterReading)
  const canQuery = perms.canMaintain || perms.canAdmin
  const who = (id: string | null) => id ? (users.find(u => u.id === id)?.name ?? "Another user") : "System"

  const renderHistory = () => isLoading ? (
    <div style={{ padding: 20, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading...</div>
  ) : rows.length === 0 ? (
    <div style={{ padding: 20, textAlign: "center", color: "var(--hf-text-faint)" }}>No readings recorded yet. The first one you enter will appear here.</div>
  ) : (
    <ol style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 8 }}>
      {rows.map(r => (
        <li key={r.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "9px 14px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, fontWeight: 600 }}>
            <span>{r.previousReading != null ? `${Number(r.previousReading).toLocaleString()} → ` : ""}{Number(r.reading).toLocaleString()} {r.meterType === "KILOMETRES" ? "km" : "hrs"}</span>
            <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>{fmtDateTime(r.recordedAt)}</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 2 }}>{SOURCE_LABEL[r.source] ?? r.source} · {who(r.actorUserId)}</div>
          {r.reason && <div style={{ fontSize: 13, marginTop: 4 }}>{r.reason}</div>}
          {r.disputeStatus && (
            <div style={{ fontSize: 12, marginTop: 4, fontWeight: r.disputeStatus === "OPEN" ? 700 : 400,
              color: r.disputeStatus === "OPEN" ? "var(--hf-warning-text)" : "var(--hf-text-muted)" }}>
              {DISPUTE_LABEL[r.disputeStatus] ?? r.disputeStatus}{r.disputeReason ? `: ${r.disputeReason}` : ""}
            </div>
          )}
          {r.meterType === "KILOMETRES" && canQuery && (
            <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
              {r.disputeStatus === "OPEN" ? (perms.canMaintain && r.disputeId && (
                <button style={btn()} onClick={() => { setErr(""); setText(""); setTarget(r); setMode("confirm") }}>Confirm reading is right</button>
              )) : !rows.some(x => x.correctsId === r.id) && (
                <button style={btn()} onClick={() => { setErr(""); setText(""); setTarget(r); setMode("dispute") }}>Dispute</button>
              )}
            </div>
          )}
        </li>
      ))}
    </ol>
  )

  const renderCorrect = () => (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
        Use this only when a reading was entered wrongly. The old reading stays in the history, and the reason is kept.
      </div>
      <div><label htmlFor="mt-corrected" style={lbl}>Correct reading ({unit}) *</label>
        <input id="mt-corrected" type="number" value={corr.reading} onChange={e => setCorr(c => ({ ...c, reading: e.target.value }))} style={inputStyle()} /></div>
      <div><label htmlFor="mt-reason" style={lbl}>Reason *</label>
        <input id="mt-reason" value={corr.reason} onChange={e => setCorr(c => ({ ...c, reason: e.target.value }))} placeholder="e.g. typed an extra zero" style={inputStyle()} /></div>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button style={btn()} onClick={back}>Cancel</button>
        <button style={btn(true)} disabled={!corr.reading || corr.reason.trim().length < 5 || correct.isPending}
          onClick={() => correct.mutate({ id: asset.id, reading: Number(corr.reading), reason: corr.reason },
            { onError: (e: unknown) => setErr(apiMessage(e, "Could not correct the reading")) })}>Correct reading</button>
      </div>
    </div>
  )

  const renderQuery = () => {
    const disputing = mode === "dispute"
    const busy = dispute.isPending || confirm.isPending
    const send = () => {
      if (!target) return
      const onError = { onError: (e: unknown) => setErr(apiMessage(e, disputing ? "Could not dispute the reading" : "Could not confirm the reading")) }
      if (disputing) dispute.mutate({ assetId: asset.id, readingId: target.id, reason: text.trim() }, onError)
      else if (target.disputeId) confirm.mutate({ assetId: asset.id, disputeId: target.disputeId, note: text.trim() }, onError)
    }
    return (
      <div style={{ display: "grid", gap: 12 }}>
        <div style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
          {disputing
            ? "While a reading is disputed, no kilometre charge can be raised on it and charges already built on it are held back from invoicing. Confirm the reading, or correct it, to carry on."
            : "Confirming says the reading is right. Charges held back for it are released."}
          {target && <> Reading: <strong>{Number(target.reading).toLocaleString()} {unit}</strong>.</>}
        </div>
        <div><label htmlFor="mt-query" style={lbl}>{disputing ? "What is disputed? *" : "Why does the reading stand? *"}</label>
          <input id="mt-query" value={text} onChange={e => setText(e.target.value)} style={inputStyle()}
            placeholder={disputing ? "e.g. the driver says it was 10 850 km" : "e.g. checked against the logbook"} /></div>
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <button style={btn()} onClick={back}>Cancel</button>
          <button style={btn(true)} disabled={text.trim().length < 5 || busy} onClick={send}>{disputing ? "Dispute reading" : "Confirm reading"}</button>
        </div>
      </div>
    )
  }

  const renderChange = () => (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
        Switch this machine from {word} to {otherWord}. Every number changes meaning, so give all three again in {otherWord}.
      </div>
      <div><label htmlFor="mc-current" style={lbl}>Current reading ({otherWord}) *</label>
        <input id="mc-current" type="number" value={chg.current} onChange={e => setChg(c => ({ ...c, current: e.target.value }))} style={inputStyle()} /></div>
      <div><label htmlFor="mc-last" style={lbl}>Reading at last service ({otherWord}) *</label>
        <input id="mc-last" type="number" value={chg.lastService} onChange={e => setChg(c => ({ ...c, lastService: e.target.value }))} style={inputStyle()} /></div>
      <div><label htmlFor="mc-interval" style={lbl}>Service interval ({otherWord}) *</label>
        <input id="mc-interval" type="number" value={chg.interval} onChange={e => setChg(c => ({ ...c, interval: e.target.value }))} style={inputStyle()} /></div>
      <div><label htmlFor="mc-reason" style={lbl}>Reason *</label>
        <input id="mc-reason" value={chg.reason} onChange={e => setChg(c => ({ ...c, reason: e.target.value }))} style={inputStyle()} /></div>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button style={btn()} onClick={back}>Cancel</button>
        <button style={btn(true)} disabled={!chg.current || !chg.lastService || !chg.interval || chg.reason.trim().length < 5 || change.isPending}
          onClick={() => change.mutate({ id: asset.id, body: {
            meterType: otherType, currentReading: Number(chg.current), lastServiceReading: Number(chg.lastService),
            serviceInterval: Number(chg.interval), reason: chg.reason } },
            { onError: (e: unknown) => setErr(apiMessage(e, "Could not change the meter")) })}>Change meter</button>
      </div>
    </div>
  )

  return (
    <Overlay onClose={onClose} width={600}>
      <ModalHead title={`Meter — ${asset.fleetNumber ?? asset.name}`}
        subtitle={`Counts ${word}. Now ${Number(asset.currentHours ?? 0).toLocaleString()} ${unit}`} onClose={onClose} />
      {mode === "history" && (perms.canMaintain || perms.canAdmin) && (
        <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
          {perms.canMaintain && <button style={btn()} onClick={() => { setErr(""); setNotice(""); setMode("correct") }}>Correct a reading</button>}
          {perms.canAdmin && <button style={btn()} onClick={() => { setErr(""); setMode("change") }}>{`Change to ${otherWord}`}</button>}
        </div>
      )}
      {notice && mode === "history" && <div role="status" style={{ marginBottom: 12, padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13 }}>{notice}</div>}
      {err && <ErrBanner msg={err} />}
      {mode === "history" ? renderHistory() : mode === "correct" ? renderCorrect() : mode === "change" ? renderChange() : renderQuery()}
    </Overlay>
  )
}
