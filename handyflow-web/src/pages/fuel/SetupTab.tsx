import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ReasonModal } from "../warehousing/whseUi"
import { DIVISIONS, EMPTY_WIZARD, LAST_STEP, ORGS, SIZES, toWizardRequest, toggleDivision, validateStep, wizardFromProfile, type Division, type Profile, type WizardState } from "./setupOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

export default function SetupTab() {
  const canManage = usePermissions(["FUEL_CONFIGURATION_MANAGE"])
  const qc = useQueryClient()
  const q = useQuery<Profile>({ queryKey: ["fuel-profile"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/operating-profile")) })
  const [w, setW] = useState<WizardState>(EMPTY_WIZARD)
  const [err, setErr] = useState("")
  const [off, setOff] = useState<Division | null>(null)
  useEffect(() => { if (q.data) setW(wizardFromProfile(q.data)) }, [q.data])
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-profile"] })
  const save = useMutation({ mutationFn: (s: WizardState) => apiClient.put("/api/v1/fuel/operating-profile/wizard", toWizardRequest(s)), onSuccess: refresh, onError: (e: any) => setErr(msg(e, "Could not save your progress")) })
  const activate = useMutation({ mutationFn: (d: Division[]) => apiClient.post("/api/v1/fuel/operating-profile/activate", { divisions: d }), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not complete setup")) })
  const turnOn = useMutation({ mutationFn: (d: Division) => apiClient.post(`/api/v1/fuel/operating-profile/divisions/${d}/activate`), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not turn the division on")) })
  const turnOff = useMutation({ mutationFn: (a: { d: Division; reason: string }) => apiClient.post(`/api/v1/fuel/operating-profile/divisions/${a.d}/deactivate`, { reason: a.reason }), onSuccess: () => { setOff(null); refresh() } })

  if (q.isLoading || !q.data) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  const p = q.data
  const label = (d: string) => DIVISIONS.find(x => x.id === d)?.label ?? d

  const checklist = (
    <div style={{ ...card, padding: 16, marginTop: 14 }}>
      <h3 style={{ margin: "0 0 8px", fontSize: 14 }}>Setup checklist</h3>
      <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
        {p.checklist.map(c => <li key={c.key}>{c.done ? "✓" : "○"} {c.label}</li>)}
      </ul>
    </div>
  )

  if (p.status === "ACTIVE" && !p.legacyMode) {
    return (
      <div>
        <div style={{ ...card, padding: 16 }}>
          <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Divisions</h3>
          <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>Turning a division off hides its screens and blocks its actions. Your records are kept.</p>
          {DIVISIONS.map(d => {
            const v = p.divisions.find(x => x.division === d.id)
            const on = !!v?.active
            return (
              <div key={d.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 0", borderTop: "1px solid var(--hf-border)" }}>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: 13 }}>{d.label}</strong> <Chip bg={on ? "var(--hf-success-bg)" : "var(--hf-surface-2)"} fg={on ? "var(--hf-success-text-strong)" : "var(--hf-text-muted)"}>{on ? "On" : "Off"}</Chip>
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{d.blurb}</div>
                  {!on && v?.deactivationReason && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>Turned off: {v.deactivationReason}</div>}
                </div>
                {canManage && (on
                  ? <button style={btnGhost} onClick={() => setOff(d.id)} aria-label={`Turn off ${d.label}`}>Turn off</button>
                  : <button style={btnPrimary} disabled={turnOn.isPending} onClick={() => turnOn.mutate(d.id)} aria-label={`Turn on ${d.label}`}>Turn on</button>)}
              </div>
            )
          })}
          {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
          {!canManage && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>You can view these settings but not change them.</p>}
        </div>
        {checklist}
        {off && <ReasonModal title={`Turn off ${label(off)}`} intro="The server refuses this while the division still has open work, and tells you what." confirmLabel="Turn off" pending={turnOff.isPending} error={turnOff.error}
          onConfirm={reason => turnOff.mutate({ d: off, reason })} onClose={() => setOff(null)} />}
      </div>
    )
  }

  const next = () => {
    const v = validateStep(w); if (v) return setErr(v)
    setErr("")
    const s = { ...w, step: Math.min(w.step + 1, LAST_STEP) }
    setW(s); save.mutate(s)
  }
  const back = () => { const s = { ...w, step: Math.max(w.step - 1, 0) }; setErr(""); setW(s); save.mutate(s) }
  const pick = (k: "businessSize" | "orgStructure", v: string) => { setErr(""); setW(s => ({ ...s, [k]: v })) }
  const opt = (selected: boolean) => ({ ...card, padding: 12, marginBottom: 8, cursor: "pointer", display: "flex", gap: 10, alignItems: "flex-start", borderColor: selected ? "var(--hf-primary)" : undefined } as React.CSSProperties)

  return (
    <div style={{ maxWidth: 720 }}>
      {p.legacyMode && (
        <div role="status" style={{ ...card, padding: 12, marginBottom: 14, fontSize: 13 }}>
          Your company used Fuel before divisions existed, so every division is on and nothing is blocked. Complete this setup to choose the divisions you actually run.
        </div>
      )}
      <div style={{ ...card, padding: 16 }}>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>Step {w.step + 1} of {LAST_STEP + 1}. Your progress is saved as you go.</div>
        {w.step === 0 && (
          <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
            <legend style={{ fontSize: 15, fontWeight: 600, marginBottom: 8 }}>Which divisions do you run?</legend>
            {DIVISIONS.map(d => (
              <label key={d.id} style={opt(w.divisions.includes(d.id))}>
                <input type="checkbox" checked={w.divisions.includes(d.id)} disabled={!canManage} onChange={() => { setErr(""); setW(s => ({ ...s, divisions: toggleDivision(s.divisions, d.id) })) }} />
                <span><strong>{d.label}</strong><br /><span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{d.blurb}</span></span>
              </label>
            ))}
          </fieldset>
        )}
        {w.step === 1 && (
          <div>
            <h3 style={{ margin: "0 0 8px", fontSize: 15 }}>About your business</h3>
            <fieldset style={{ border: 0, padding: 0, margin: "0 0 10px" }}>
              <legend style={{ fontSize: 13, fontWeight: 600 }}>Business size</legend>
              {SIZES.map(s => <label key={s.id} style={{ display: "block", fontSize: 13, padding: "3px 0" }}><input type="radio" name="size" checked={w.businessSize === s.id} disabled={!canManage} onChange={() => pick("businessSize", s.id)} /> {s.label}</label>)}
            </fieldset>
            <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
              <legend style={{ fontSize: 13, fontWeight: 600 }}>Structure</legend>
              {ORGS.map(s => <label key={s.id} style={{ display: "block", fontSize: 13, padding: "3px 0" }}><input type="radio" name="org" checked={w.orgStructure === s.id} disabled={!canManage} onChange={() => pick("orgStructure", s.id)} /> {s.label}</label>)}
            </fieldset>
          </div>
        )}
        {w.step === LAST_STEP && (
          <div>
            <h3 style={{ margin: "0 0 8px", fontSize: 15 }}>Review and activate</h3>
            <p style={{ fontSize: 13, margin: "0 0 6px" }}>Divisions: <strong>{w.divisions.map(label).join(", ") || "none chosen"}</strong></p>
            <p style={{ fontSize: 13, margin: "0 0 6px" }}>Size: {SIZES.find(s => s.id === w.businessSize)?.label ?? "–"}. Structure: {ORGS.find(s => s.id === w.orgStructure)?.label ?? "–"}.</p>
            <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>You can turn divisions on or off later from this page. Records are never deleted.</p>
          </div>
        )}
        {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
        {canManage ? (
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            {w.step > 0 && <button style={btnGhost} onClick={back}>Back</button>}
            {w.step < LAST_STEP
              ? <button style={btnPrimary} onClick={next}>Next</button>
              : <button style={btnPrimary} disabled={activate.isPending || w.divisions.length === 0} onClick={() => activate.mutate(w.divisions)}>Activate Fuel</button>}
          </div>
        ) : <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Only someone who can manage Fuel configuration can complete setup.</p>}
      </div>
      {checklist}
    </div>
  )
}
