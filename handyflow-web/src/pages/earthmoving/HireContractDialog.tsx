// src/pages/earthmoving/HireContractDialog.tsx
import { useState } from "react"
import { Link } from "react-router-dom"
import { Overlay, ModalHead, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { CONTRACT_STATUS_LABEL, HIRE_BASIS_LABEL } from "./shared/constants"
import { apiMessage, fmtCurrency2dp } from "./shared/format"
import { useEarthPerms } from "./shared/useEarthPerms"
import {
  useAssetDeployments, useDeploymentContract, useDraftHireContract, useLinkHireContract, useExtendHire,
  useHireContractOptions, useEarthSettings, useSaveSettings, useContractRates, useSaveContractRate, useRemoveContractRate, useAutoBilling, useSaveAutoBilling,
} from "./shared/hooks"
import type { Asset, ContractRef } from "./shared/types"

const btn: React.CSSProperties = { padding: "8px 14px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 13, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none" }

function ContractLine({ c }: { c: ContractRef }) {
  const signed = c.status === "SIGNED"
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13 }}>
      <span><strong>{c.number ?? "Contract"}</strong> — {c.title}</span>
      <span style={{ fontWeight: 700, color: signed ? "var(--hf-success-text-strong)" : "var(--hf-warning-text)" }}>{CONTRACT_STATUS_LABEL[c.status] ?? c.status}</span>
    </div>
  )
}

/**
 * The rates agreed in the contract. A hire charge uses these before the machine's own rate. Changing one affects charges
 * raised afterwards, never ones already raised. Everyone with read access sees them; administrators change them.
 */
function ContractRates({ deploymentId, canEdit }: { deploymentId: string; canEdit: boolean }) {
  const { data } = useContractRates(deploymentId)
  const save = useSaveContractRate(deploymentId)
  const remove = useRemoveContractRate(deploymentId)
  const [basis, setBasis] = useState("DAILY")
  const [rate, setRate] = useState("")
  const [included, setIncluded] = useState("")
  const [err, setErr] = useState("")
  const rates = data?.rates ?? []
  const km = basis === "KILOMETRE"
  const valid = Number(rate) > 0 && (!km || included.trim() === "" || Number(included) >= 0)
  const edit = (b: string) => {
    const r = rates.find(x => x.basis === b)
    setBasis(b); setRate(r ? String(r.unitRate) : ""); setIncluded(r?.includedKm != null ? String(r.includedKm) : ""); setErr("")
  }
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={lbl}>Rates on this contract</div>
      {rates.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No rates yet. Charges use the machine's own rate, or a rate agreed per charge.</div>
        : rates.map(r => (
          <div key={r.basis} style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13 }}>
            <span>{HIRE_BASIS_LABEL[r.basis] ?? r.basis}: <strong>{fmtCurrency2dp(r.unitRate)}</strong>{r.includedKm != null ? ` · ${r.includedKm} km included` : ""}</span>
            {canEdit && (
              <span style={{ display: "flex", gap: 6 }}>
                <button style={btn} onClick={() => edit(r.basis)}>Change</button>
                <button style={btn} disabled={remove.isPending} aria-label={`Remove ${HIRE_BASIS_LABEL[r.basis] ?? r.basis} rate`}
                  onClick={() => { setErr(""); remove.mutate(r.basis, { onError: (e: unknown) => setErr(apiMessage(e, "Could not remove the rate")) }) }}>Remove</button>
              </span>
            )}
          </div>
        ))}
      {canEdit && (
        <div style={{ display: "grid", gridTemplateColumns: km ? "1fr 1fr 1fr" : "1fr 1fr", gap: 10, alignItems: "end" }}>
          <div><label htmlFor="cr-basis" style={lbl}>Rate for</label>
            <select id="cr-basis" value={basis} onChange={e => edit(e.target.value)} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
              {Object.entries(HIRE_BASIS_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
          <div><label htmlFor="cr-rate" style={lbl}>Rate (rand)</label>
            <input id="cr-rate" type="number" min="0" value={rate} onChange={e => setRate(e.target.value)} style={inputStyle()} /></div>
          {km && <div><label htmlFor="cr-incl" style={lbl}>Kilometres included</label>
            <input id="cr-incl" type="number" min="0" value={included} onChange={e => setIncluded(e.target.value)} style={inputStyle()} /></div>}
          <div style={{ gridColumn: "1 / -1", display: "flex", justifyContent: "flex-end" }}>
            <button style={primary} disabled={!valid || save.isPending}
              onClick={() => { setErr(""); save.mutate({ basis, unitRate: Number(rate), includedKm: km && included.trim() !== "" ? Number(included) : null },
                { onError: (e: unknown) => setErr(apiMessage(e, "Could not save the rate")), onSuccess: () => { setRate(""); setIncluded("") } }) }}>Save rate</button>
          </div>
        </div>
      )}
      {err && <ErrBanner msg={err} />}
    </div>
  )
}

/** Whether the monthly billing run charges this deployment, and by what. Administrators change it. */
function AutoBillingSection({ deploymentId, canEdit }: { deploymentId: string; canEdit: boolean }) {
  const { data } = useAutoBilling(deploymentId)
  const save = useSaveAutoBilling(deploymentId)
  const [pick, setPick] = useState<string | null>(null)
  const [err, setErr] = useState("")
  const current = data?.basis ?? ""
  const shown = pick ?? current
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={lbl}>Monthly billing</div>
      {canEdit ? (
        <div style={{ display: "flex", gap: 8, alignItems: "end" }}>
          <div style={{ flex: 1 }}>
            <label htmlFor="ab-basis" style={lbl}>Charge this hire each month</label>
            <select id="ab-basis" value={shown} onChange={e => setPick(e.target.value)} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
              <option value="">Not automatically</option>
              {Object.entries(HIRE_BASIS_LABEL).map(([v, l]) => <option key={v} value={v}>{l.replace("Per ", "By the ")}</option>)}
            </select>
          </div>
          <button style={primary} disabled={shown === current || save.isPending}
            onClick={() => { setErr(""); save.mutate(shown === "" ? null : shown, { onError: (e: unknown) => setErr(apiMessage(e, "Could not save")), onSuccess: () => setPick(null) }) }}>Save</button>
        </div>
      ) : (
        <div style={{ fontSize: 13 }}>{current ? `Billed automatically each month: ${(HIRE_BASIS_LABEL[current] ?? current).toLowerCase()}.` : "Not billed automatically."}</div>
      )}
      {err && <ErrBanner msg={err} />}
    </div>
  )
}

/** The hire contract of the machine's current deployment: see it, make a draft, link one, or record an extension. */
export function HireContractDialog({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const perms = useEarthPerms()
  const { data: deployments = [], isLoading: loadingDeps } = useAssetDeployments(asset.id)
  const open = deployments.find(d => !d.returnedAt)
  const id = open?.id ?? ""
  const { data: panel, isLoading, isError } = useDeploymentContract(id)
  const draft = useDraftHireContract(id)
  const link = useLinkHireContract(id)
  const extend = useExtendHire(id)
  const [mode, setMode] = useState<"" | "link" | "extend">("")
  const [pick, setPick] = useState("")
  const [newEnd, setNewEnd] = useState("")
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const { data: options = [], isError: noList } = useHireContractOptions(mode === "link")
  const fail = (fallback: string) => ({ onError: (e: unknown) => setErr(apiMessage(e, fallback)) })

  return (
    <Overlay onClose={onClose} width={560}>
      <ModalHead title="Hire contract" subtitle={`${asset.fleetNumber ? asset.fleetNumber + " — " : ""}${asset.name}`} onClose={onClose} />
      {loadingDeps || (id && isLoading) ? <div>Loading…</div>
        : !open ? <div>This machine has no open deployment.</div>
        : isError || !panel ? <ErrBanner msg="The contract details could not be loaded." />
        : (
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
              Hire value about <strong>{fmtCurrency2dp(panel.expectedValue)}</strong>
              {panel.threshold != null ? <> · a signed contract is expected from {fmtCurrency2dp(panel.threshold)}</> : <> · no contract threshold is set</>}
            </div>
            {panel.warning && <div role="status" style={{ padding: "10px 12px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text)" }}>{panel.warning} This is a warning only.</div>}

            {panel.contract ? (
              <>
                <ContractLine c={panel.contract} />
                <Link to="/contracts" style={{ fontSize: 12 }}>Open Contracting to complete, send or sign it</Link>
              </>
            ) : <div style={{ fontSize: 13 }}>No contract is linked to this hire.</div>}

            {panel.contract && <ContractRates deploymentId={id} canEdit={perms.canAdmin} />}

            <AutoBillingSection deploymentId={id} canEdit={perms.canAdmin} />

            {panel.addenda.length > 0 && (
              <div>
                <div style={lbl}>Extensions</div>
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>{panel.addenda.map(c => <ContractLine key={c.id} c={c} />)}</div>
              </div>
            )}

            {perms.canDeploy && !mode && (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                {!panel.contract && <button style={primary} disabled={draft.isPending}
                  onClick={() => { setErr(""); draft.mutate(undefined, fail("Could not make the contract")) }}>Make a draft contract</button>}
                {!panel.contract && <button style={btn} onClick={() => { setErr(""); setMode("link") }}>Link an existing contract</button>}
                {panel.contract && <button style={btn} onClick={() => { setErr(""); setMode("extend") }}>Record an extension</button>}
              </div>
            )}

            {mode === "link" && (
              <div>
                <label htmlFor="hc-pick" style={lbl}>Hire agreement</label>
                {noList ? <div style={{ fontSize: 13 }}>You cannot browse Contracting, so an existing contract cannot be picked here.</div> : (
                  <select id="hc-pick" value={pick} onChange={e => setPick(e.target.value)} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                    <option value="">Choose a contract…</option>
                    {options.map(c => <option key={c.id} value={c.id}>{c.number ?? "—"} — {c.title} ({CONTRACT_STATUS_LABEL[c.status] ?? c.status})</option>)}
                  </select>
                )}
                <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
                  <button style={btn} onClick={() => setMode("")}>Cancel</button>
                  <button style={primary} disabled={!pick || link.isPending}
                    onClick={() => link.mutate(pick, { ...fail("Could not link the contract"), onSuccess: () => { setMode(""); setPick("") } })}>Link contract</button>
                </div>
              </div>
            )}

            {mode === "extend" && (
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                  This makes a draft addendum under the contract. The planned return date is not changed by it.
                </div>
                <div>
                  <label htmlFor="hc-end" style={lbl}>New end date *</label>
                  <input id="hc-end" type="date" value={newEnd} onChange={e => setNewEnd(e.target.value)} style={inputStyle()} />
                </div>
                <div>
                  <label htmlFor="hc-why" style={lbl}>Reason *</label>
                  <input id="hc-why" value={reason} onChange={e => setReason(e.target.value)} placeholder="Customer needs the machine longer" style={inputStyle()} />
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={btn} onClick={() => setMode("")}>Cancel</button>
                  <button style={primary} disabled={!newEnd || reason.trim().length < 5 || extend.isPending}
                    onClick={() => extend.mutate({ newEndDate: newEnd, reason }, { ...fail("Could not record the extension"), onSuccess: () => { setMode(""); setNewEnd(""); setReason("") } })}>Create addendum</button>
                </div>
              </div>
            )}
            {err && <ErrBanner msg={err} />}
          </div>
        )}
    </Overlay>
  )
}

/** The hire value from which a signed contract is expected. Administrators change it; everyone else sees it. */
export function ContractSettingsDialog({ onClose }: { onClose: () => void }) {
  const perms = useEarthPerms()
  const { data, isLoading } = useEarthSettings()
  const [text, setText] = useState<string | null>(null)
  const [err, setErr] = useState("")
  const save = useSaveSettings(onClose)
  const shown = text ?? (data?.contractThreshold != null ? String(data.contractThreshold) : "")
  const value = shown.trim() === "" ? null : Number(shown)
  const bad = value !== null && (!Number.isFinite(value) || value < 0)

  return (
    <Overlay onClose={onClose} width={480}>
      <ModalHead title="Contract threshold" onClose={onClose} />
      {isLoading ? <div>Loading…</div> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
            A hire worth this much or more is expected to have a signed contract. Smaller hires use your standard terms. Leave it empty to never expect one. Missing contracts are warnings only.
          </div>
          <div>
            <label htmlFor="ct-thr" style={lbl}>Threshold (rand)</label>
            <input id="ct-thr" inputMode="decimal" value={shown} disabled={!perms.canAdmin} onChange={e => setText(e.target.value)} placeholder="Not set" style={inputStyle(bad)} />
          </div>
          {!perms.canAdmin && <div style={{ fontSize: 12 }}>Only an administrator can change this.</div>}
          {err && <ErrBanner msg={err} />}
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <button style={btn} onClick={onClose}>Close</button>
            {perms.canAdmin && <button style={primary} disabled={bad || save.isPending}
              onClick={() => save.mutate(value, { onError: (e: unknown) => setErr(apiMessage(e, "Could not save")) })}>Save</button>}
          </div>
        </div>
      )}
    </Overlay>
  )
}
