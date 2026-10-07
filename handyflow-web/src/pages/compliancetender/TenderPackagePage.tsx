// src/pages/compliancetender/TenderPackagePage.tsx
//
// Submission package for one of the tenant's own tenders (ADR-005): choose what goes in and in what order, see at a glance what is ready and what is blocking submission, then build a
// versioned, hashed package. The server plans and validates; this page sends the choices (debounced preview) and shows the answer in plain words.
import { Fragment, useEffect, useMemo, useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import Chip from "../../components/ui/Chip"
import {
  AlertOctagon, AlertTriangle, ArrowDown, ArrowLeft, ArrowUp, CheckCircle2, Clock, Download, FileCheck2, FileText, FolderCheck, Hammer, History, ListChecks, Package, ShieldAlert,
} from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import ProgressRing from "../../components/ui/ProgressRing"
import {
  downloadPackage, useBuildPackage, useComplianceDocuments, usePackagePreview, usePackages, useSubmissionProfiles,
  type PackagePlan, type TenderPackage,
} from "./package.api"
import {
  COVER_LETTER_TEMPLATE, DEFAULT_REQUEST, EMPTY_LIMITS, INITIAL_DRAFT, SECTIONS, blocking, closingText, documentState, feed, fmtSize, fmtWhen, headline, moveSection, sectionTone, sectionsReady, shortHash, sourceText,
  staleLatest, toRequest, toggleDocument, toggleSection, warnings, type LimitsDraft, type PackageDraft,
} from "./package.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 20 }
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box", fontFamily: "inherit" }
const label: React.CSSProperties = { display: "block", fontSize: 11.5, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 4 }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: "pointer", border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-sky-text-strong)" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-sky-text-strong)", color: "var(--hf-surface)", border: "1px solid transparent" }
const h3: React.CSSProperties = { fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: 0, display: "flex", alignItems: "center", gap: 8 }
const iconBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", justifyContent: "center", width: 26, height: 26, borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer", padding: 0 }

const TONE_STYLE = {
  ready: { bg: "var(--hf-success-soft)", border: "var(--hf-success-border-subtle)", fg: "var(--hf-success-text-strong)" },
  draft: { bg: "var(--hf-warning-soft)", border: "var(--hf-warning-border)", fg: "var(--hf-warning-text-deep)" },
  blocked: { bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)", fg: "var(--hf-danger-text-strong)" },
} as const

const apiMessage = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string } | null
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value)
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t) }, [value, ms])
  return v
}

export default function TenderPackagePage() {
  const { id } = useParams<{ id: string }>()
  const canManage = usePermission("COMPLIANCE_MANAGE")
  const canAdmin = usePermission("COMPLIANCE_ADMIN")
  const canRead = usePermission("COMPLIANCE_READ")
  const mayPrice = canManage || canAdmin
  const today = useMemo(() => new Date(), [])

  const { data: tender } = useQuery<{ name: string; tenderNumber: string; closingDate: string | null; status: string; requiresPricing?: boolean }>({
    queryKey: ["ct-tender", id], enabled: !!id,
    queryFn: async () => (await apiClient.get(`/api/v1/compliance/tenders/${id}`)).data,
  })
  const [draft, setDraft] = useState<PackageDraft>(INITIAL_DRAFT)
  const set = (patch: Partial<PackageDraft>) => setDraft(d => ({ ...d, ...patch }))

  const parsed = useMemo(() => toRequest(draft), [draft])
  const debounced = useDebounced(parsed.ok ? parsed.request : null, 450)
  const preview = usePackagePreview(id, debounced ?? DEFAULT_REQUEST, !!debounced)
  const packages = usePackages(id)
  const docs = useComplianceDocuments()
  const profiles = useSubmissionProfiles()
  const build = useBuildPackage(id ?? "")
  const [buildError, setBuildError] = useState("")
  const [justBuilt, setJustBuilt] = useState<TenderPackage | null>(null)

  if (!canRead && !mayPrice) {
    return <div style={{ maxWidth: 1180, margin: "0 auto" }}><p role="alert" style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>Submission packages are only available to people who can view compliance.</p></div>
  }

  const plan: PackagePlan | undefined = build.data && !build.data.built ? build.data : preview.data
  const closing = closingText(tender?.closingDate, today)
  const locked = !!tender && !["DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT"].includes(tender.status)

  async function runBuild() {
    if (!parsed.ok) return
    setBuildError(""); setJustBuilt(null)
    try {
      const result = await build.mutateAsync(parsed.request)
      if (result.built) setJustBuilt(result.built)
    } catch (e) { setBuildError(apiMessage(e)) }
  }

  return (
    <div style={{ maxWidth: 1180, margin: "0 auto" }}>
      <Link to={`/compliancetender/tenders/${id}`} style={{ display: "inline-flex", alignItems: "center", gap: 6, color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 14, textDecoration: "none" }}>
        <ArrowLeft size={15} /> Back to tender
      </Link>

      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, flexWrap: "wrap", marginBottom: 18 }}>
        <div>
          <h1 style={{ margin: "0 0 2px", fontSize: 22, fontWeight: 800, color: "var(--hf-text)", display: "flex", alignItems: "center", gap: 10 }}><Package size={22} aria-hidden="true" /> Submission package</h1>
          <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-faint)" }}>{tender ? `${tender.tenderNumber} · ${tender.name}` : " "}</p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 10, ...card, padding: "10px 16px", borderRadius: 12 }}>
          <Clock size={20} aria-hidden="true" style={{ color: closing.tone === "late" ? "var(--hf-danger)" : closing.tone === "soon" ? "var(--hf-warning)" : "var(--hf-text-muted)" }} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 800, color: "var(--hf-text)" }}>{closing.text}</div>
            <div style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{tender?.closingDate ? `Due: ${tender.closingDate}` : "Set a closing date on the tender"}</div>
          </div>
        </div>
      </header>

      {locked && (
        <p role="status" style={{ margin: "0 0 14px", padding: "10px 14px", background: "var(--hf-surface-sunken)", border: "1px solid var(--hf-border)", borderRadius: 10, fontSize: 13, color: "var(--hf-text-secondary)" }}>
          This tender is {tender?.status.toLowerCase().replace(/_/g, " ")}, so no new package can be built. Earlier versions below stay available to download.
        </p>
      )}

      {!parsed.ok && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "0 0 12px" }}>{parsed.error}</p>}
      {preview.isError && !plan && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "0 0 12px" }}>We couldn't check the package. {apiMessage(preview.error)}</p>}

      {plan && <StatusBanner plan={plan} building={build.isPending} canBuild={!locked && parsed.ok && plan.canBuild} onBuild={runBuild} />}
      {buildError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "0 0 12px" }}>{buildError}</p>}
      {justBuilt && (
        <p role="status" style={{ display: "flex", alignItems: "center", gap: 8, margin: "0 0 14px", padding: "10px 14px", background: "var(--hf-success-soft)", border: "1px solid var(--hf-success-border-subtle)", borderRadius: 10, fontSize: 13, color: "var(--hf-success-text-strong)" }}>
          <CheckCircle2 size={16} aria-hidden="true" /> Version {justBuilt.versionNo} built · {fmtSize(justBuilt.sizeBytes)}{justBuilt.pageCount ? ` · ${justBuilt.pageCount} pages` : ""}.
          <button type="button" onClick={() => downloadPackage(justBuilt.id, justBuilt.fileName)} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700, fontSize: 13 }}>Download</button>
        </p>
      )}

      {plan && <Kpis plan={plan} documentCount={draft.documentIds.length} />}

      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start", marginTop: 16 }}>
        <div style={{ flex: "2 1 460px", minWidth: 0, display: "flex", flexDirection: "column", gap: 16 }}>
          <Contents draft={draft} set={set} plan={plan} mayPrice={mayPrice} docs={docs.data ?? []} docsLoading={docs.isLoading} closingIso={tender?.closingDate} today={today} tenderRequiresPricing={tender?.requiresPricing} />
        </div>
        <div style={{ flex: "1 1 320px", minWidth: 0, display: "flex", flexDirection: "column", gap: 16 }}>
          <Feed plan={plan} />
          <Rules draft={draft} set={set} saved={profiles.data ?? []} notes={plan?.limitNotes ?? []} />
        </div>
      </div>

      {(() => {
        const stale = staleLatest(packages.data ?? [])
        return stale ? (
          <div role="status" style={{ marginBottom: 14, padding: "10px 14px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 10, fontSize: 13, color: "var(--hf-warning-text)" }}>
            <strong>Version {stale.versionNo} is out of date.</strong> {(stale.staleReasons ?? []).join(". ")}. Build a new version before you submit.
          </div>
        ) : null
      })()}
      <Versions packages={packages.data ?? []} loading={packages.isLoading} />
    </div>
  )
}

// ---- status banner

function StatusBanner({ plan, building, canBuild, onBuild }: { plan: PackagePlan; building: boolean; canBuild: boolean; onBuild: () => void }) {
  const h = headline(plan)
  const t = TONE_STYLE[h.tone]
  const Icon = h.tone === "ready" ? CheckCircle2 : h.tone === "draft" ? AlertTriangle : AlertOctagon
  return (
    <section aria-label="Package status" style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap", padding: "14px 18px", borderRadius: 14, background: t.bg, border: `1px solid ${t.border}`, marginBottom: 14 }}>
      <Icon size={26} aria-hidden="true" style={{ color: t.fg, flexShrink: 0 }} />
      <div style={{ flex: "1 1 240px" }}>
        <div style={{ fontSize: 15, fontWeight: 800, color: t.fg }}>{h.title}</div>
        <div style={{ fontSize: 13, color: t.fg, opacity: 0.9 }}>{h.detail}</div>
      </div>
      <button type="button" onClick={onBuild} disabled={!canBuild || building} style={{ ...primary, opacity: !canBuild || building ? 0.55 : 1, cursor: !canBuild || building ? "not-allowed" : "pointer" }}>
        <Hammer size={15} aria-hidden="true" /> {building ? "Building…" : "Build submission package"}
      </button>
    </section>
  )
}

// ---- KPI tiles

function Kpis({ plan, documentCount }: { plan: PackagePlan; documentCount: number }) {
  const { ready, chosen } = sectionsReady(plan)
  const b = blocking(plan.issues).length, w = warnings(plan.issues).length
  const tile: React.CSSProperties = { ...card, flex: "1 1 200px", display: "flex", alignItems: "center", gap: 14, padding: "14px 18px" }
  const big: React.CSSProperties = { fontSize: 26, fontWeight: 800, color: "var(--hf-text)", lineHeight: 1 }
  const cap: React.CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }
  const badge = (bg: string, fg: string, icon: React.ReactNode) => (
    <span aria-hidden="true" style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 48, height: 48, borderRadius: 12, background: bg, color: fg, flexShrink: 0 }}>{icon}</span>
  )
  return (
    <section aria-label="Package summary" style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
      <div style={tile}>
        <ProgressRing value={ready} max={chosen} tone={ready === chosen && chosen > 0 ? "ok" : "warn"} label={`${ready} of ${chosen} sections ready`} showValue={false} />
        <div><div style={big}>{ready}<span style={{ fontSize: 15, color: "var(--hf-text-faint)" }}> / {chosen}</span></div><div style={cap}>Sections ready</div></div>
      </div>
      <div style={tile}>
        {badge(b ? "var(--hf-danger-soft-strong)" : "var(--hf-success-soft-strong)", b ? "var(--hf-danger-text-strong)" : "var(--hf-success-text-strong)", <ShieldAlert size={24} />)}
        <div><div style={{ ...big, color: b ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{b}</div><div style={cap}>Blocking submission</div></div>
      </div>
      <div style={tile}>
        {badge(w ? "var(--hf-warning-soft-strong)" : "var(--hf-surface-sunken)", w ? "var(--hf-warning-text-deep)" : "var(--hf-text-faint)", <AlertTriangle size={24} />)}
        <div><div style={big}>{w}</div><div style={cap}>{w === 1 ? "Warning" : "Warnings"}</div></div>
      </div>
      <div style={tile}>
        {badge("var(--hf-surface-sunken)", "var(--hf-sky-text-strong)", <FolderCheck size={24} />)}
        <div><div style={big}>{documentCount}</div><div style={cap}>{documentCount === 1 ? "Document chosen" : "Documents chosen"}</div></div>
      </div>
    </section>
  )
}

// ---- contents: sections as a pipeline, with their editors

function Contents({ draft, set, plan, mayPrice, docs, docsLoading, closingIso, today, tenderRequiresPricing }: {
  tenderRequiresPricing?: boolean
  draft: PackageDraft; set: (p: Partial<PackageDraft>) => void; plan: PackagePlan | undefined; mayPrice: boolean
  docs: { id: string; documentType: string; expiryDate: string | null; verified: boolean }[]; docsLoading: boolean; closingIso: string | null | undefined; today: Date
}) {
  const ordered = [...draft.included.map(k => SECTIONS.find(s => s.key === k)).filter((s): s is NonNullable<typeof s> => !!s),
    ...SECTIONS.filter(s => !draft.included.includes(s.key))]
  return (
    <section aria-label="Package contents" style={card}>
      <h3 style={{ ...h3, marginBottom: 4 }}><ListChecks size={15} aria-hidden="true" /> Package contents</h3>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: "0 0 14px" }}>Tick what goes in and set the order. A section that can't be produced says why; it is never dropped silently.</p>

      <ol style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 8 }}>
        {ordered.map(s => {
          const on = draft.included.includes(s.key)
          const status = plan?.sections.find(x => x.key === s.key)
          const tone = on ? sectionTone(status) : "unknown"
          const pos = draft.included.indexOf(s.key)
          const edge = !on ? "var(--hf-border)" : tone === "ok" ? "var(--hf-success)" : tone === "missing" ? "var(--hf-warning)" : "var(--hf-border-strong)"
          return (
            <li key={s.key} style={{ border: "1px solid var(--hf-border)", borderLeft: `4px solid ${edge}`, borderRadius: 10, background: on ? "var(--hf-surface)" : "var(--hf-surface-muted)", opacity: on ? 1 : 0.75 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 12px" }}>
                <input type="checkbox" id={`sec-${s.key}`} checked={on} onChange={() => set({ included: toggleSection(draft.included, s.key) })} style={{ width: 16, height: 16 }} />
                <label htmlFor={`sec-${s.key}`} style={{ flex: 1, cursor: "pointer", minWidth: 0 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 700, color: "var(--hf-text)" }}>{on ? `${pos + 1}. ` : ""}{s.title}</div>
                  <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
                    {on && tone === "missing" && status?.unavailableReason ? `Not in the package: ${status.unavailableReason}` : s.key === "PRICING" && !mayPrice ? "Pricing needs manage permission, so it will be left out of your drafts" : s.hint}
                  </div>
                </label>
                {on && tone === "ok" && <span style={{ fontSize: 11.5, fontWeight: 700, color: "var(--hf-success-text-strong)", background: "var(--hf-success-soft-strong)", padding: "2px 9px", borderRadius: 999 }}>Ready{status && status.fileCount > 0 ? ` · ${status.fileCount} ${status.fileCount === 1 ? "file" : "files"}` : ""}</span>}
                {on && tone === "missing" && <span style={{ fontSize: 11.5, fontWeight: 700, color: "var(--hf-warning-text-deep)", background: "var(--hf-warning-soft-strong)", padding: "2px 9px", borderRadius: 999 }}>Missing</span>}
                {on && (
                  <span style={{ display: "inline-flex", gap: 4 }}>
                    <button type="button" aria-label={`Move ${s.title} up`} disabled={pos === 0} onClick={() => set({ included: moveSection(draft.included, s.key, -1) })} style={{ ...iconBtn, opacity: pos === 0 ? 0.4 : 1 }}><ArrowUp size={14} /></button>
                    <button type="button" aria-label={`Move ${s.title} down`} disabled={pos === draft.included.length - 1} onClick={() => set({ included: moveSection(draft.included, s.key, 1) })} style={{ ...iconBtn, opacity: pos === draft.included.length - 1 ? 0.4 : 1 }}><ArrowDown size={14} /></button>
                  </span>
                )}
              </div>

              {on && s.key === "COVER_LETTER" && (
                <div style={{ padding: "0 12px 12px 40px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <label htmlFor="cover-letter" style={label}>Cover letter text</label>
                    {draft.coverLetter.trim() === "" && (
                      <button type="button" onClick={() => set({ coverLetter: COVER_LETTER_TEMPLATE })}
                        style={{ background: "none", border: "none", color: "var(--hf-accent-text)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Start from a template</button>
                    )}
                  </div>
                  <textarea id="cover-letter" value={draft.coverLetter} onChange={e => set({ coverLetter: e.target.value })} rows={6} maxLength={20000} placeholder="Dear Sir/Madam…" style={{ ...input, resize: "vertical" }} />
                </div>
              )}

              {on && s.key === "COMPANY_PROFILE" && (
                <div style={{ padding: "0 12px 12px 40px" }}>
                  <div role="radiogroup" aria-label="Company profile source" style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 8 }}>
                    {(["CURRENT", "CUSTOM"] as const).map(m => (
                      <label key={m} style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--hf-text-secondary)", cursor: "pointer" }}>
                        <input type="radio" name="company-mode" checked={draft.companyMode === m} onChange={() => set({ companyMode: m })} />
                        {m === "CURRENT" ? "Use the current company profile" : "Customise for this tender"}
                      </label>
                    ))}
                  </div>
                  {draft.companyMode === "CUSTOM" && (
                    <>
                      <label htmlFor="company-text" style={label}>Company profile for this tender</label>
                      <textarea id="company-text" value={draft.companyText} onChange={e => set({ companyText: e.target.value })} rows={5} maxLength={20000} style={{ ...input, resize: "vertical" }} />
                    </>
                  )}
                </div>
              )}

              {on && s.key === "PRICING" && (
                <div style={{ padding: "0 12px 12px 40px" }}>
{tenderRequiresPricing ? (
                    <p style={{ fontSize: 12.5, color: "var(--hf-text-secondary)", margin: 0 }}>This tender requires pricing (set on the tender). A package without it can't be marked ready to submit.</p>
                  ) : (
                    <label style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--hf-text-secondary)", cursor: "pointer" }}>
                      <input type="checkbox" checked={draft.pricingRequired} onChange={e => set({ pricingRequired: e.target.checked })} />
                      For this build only: treat pricing as required. To record it on the tender, use Edit details.
                    </label>
                  )}
                </div>
              )}

              {on && s.key === "SUPPORTING_DOCUMENTS" && (
                <div style={{ padding: "0 12px 12px 40px" }}>
                  {docsLoading && <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>Loading documents…</p>}
                  {!docsLoading && docs.length === 0 && <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>No compliance documents uploaded yet. Add them in the Documents tab.</p>}
                  <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 6 }}>
                    {docs.map(d => {
                      const st = documentState(d, closingIso, today)
                      const colour = st.tone === "bad" ? "var(--hf-danger-text-strong)" : st.tone === "warn" ? "var(--hf-warning-text-deep)" : st.tone === "ok" ? "var(--hf-success-text-strong)" : "var(--hf-text-faint)"
                      return (
                        <li key={d.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "6px 8px", border: "1px solid var(--hf-border-subtle)", borderRadius: 8 }}>
                          <input type="checkbox" id={`doc-${d.id}`} checked={draft.documentIds.includes(d.id)} onChange={() => set({ documentIds: toggleDocument(draft.documentIds, d.id) })} />
                          <FileText size={14} aria-hidden="true" style={{ color: "var(--hf-text-faint)" }} />
                          <label htmlFor={`doc-${d.id}`} style={{ flex: 1, fontSize: 13, color: "var(--hf-text)", cursor: "pointer" }}>{d.documentType}</label>
                          <span style={{ fontSize: 11.5, fontWeight: 700, color: colour }}>{st.text}</span>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )}
            </li>
          )
        })}
      </ol>
    </section>
  )
}

// ---- "blocking submission" feed

function Feed({ plan }: { plan: PackagePlan | undefined }) {
  const items = plan ? feed(plan.issues) : []
  return (
    <section aria-label="Blocking submission" style={card}>
      <h3 style={h3}><AlertOctagon size={15} aria-hidden="true" /> Blocking submission</h3>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: "4px 0 12px" }}>Problems that stop a build come first, then things worth a look.</p>
      {!plan && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>Checking…</p>}
      {plan && items.length === 0 && (
        <p style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--hf-success-text-strong)", margin: 0 }}><CheckCircle2 size={16} aria-hidden="true" /> Nothing is blocking this package.</p>
      )}
      <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column" }}>
        {items.map((i, n) => (
          <li key={`${i.code}-${n}`} style={{ display: "flex", gap: 10, padding: "10px 0", borderTop: n === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
            {i.severity === "BLOCKING"
              ? <AlertOctagon size={16} aria-label="Blocking" style={{ color: "var(--hf-danger)", flexShrink: 0, marginTop: 1 }} />
              : <AlertTriangle size={16} aria-label="Warning" style={{ color: "var(--hf-warning)", flexShrink: 0, marginTop: 1 }} />}
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 13, color: "var(--hf-text)", overflowWrap: "anywhere" }}>{i.message}</div>
              {i.fileName && <div style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{i.fileName}</div>}
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

// ---- submission rules: saved profile plus this tender's own overrides

function Rules({ draft, set, saved, notes }: { draft: PackageDraft; set: (p: Partial<PackageDraft>) => void; saved: { id: string; name: string }[]; notes: string[] }) {
  const [open, setOpen] = useState(false)
  const l = draft.limits
  const setLimit = (patch: Partial<LimitsDraft>) => set({ limits: { ...l, ...patch } })
  const field = (id: string, text: string, value: string, key: keyof LimitsDraft, placeholder: string) => (
    <div style={{ flex: "1 1 130px" }}>
      <label htmlFor={id} style={label}>{text}</label>
      <input id={id} value={value} inputMode="decimal" placeholder={placeholder} onChange={e => setLimit({ [key]: e.target.value } as Partial<LimitsDraft>)} style={input} />
    </div>
  )
  return (
    <section aria-label="Submission rules" style={card}>
      <h3 style={h3}><FileCheck2 size={15} aria-hidden="true" /> Submission rules</h3>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: "4px 0 12px" }}>The portal's limits, from the tender instructions. Nothing is assumed: a limit you leave blank isn't checked beyond the system maximum.</p>
      <label htmlFor="profile" style={label}>Saved profile</label>
      <select id="profile" value={draft.profileId ?? ""} onChange={e => set({ profileId: e.target.value || null })} style={input}>
        <option value="">None chosen</option>
        {saved.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>
      <button type="button" onClick={() => setOpen(o => !o)} aria-expanded={open} style={{ ...btn, marginTop: 12, padding: "6px 12px", fontSize: 12.5 }}>{open ? "Hide" : "Customise for this tender"}</button>
      {open && (
        <div style={{ marginTop: 12, display: "flex", flexWrap: "wrap", gap: 10 }}>
          <div style={{ flex: "1 1 100%" }}>{field("lim-name", "Name (optional)", l.name, "name", "e.g. Eskom, this tender")}</div>
          <div style={{ flex: "1 1 100%" }}>{field("lim-ext", "Allowed file types", l.allowed, "allowed", "pdf, xlsx")}</div>
          {field("lim-file", "Largest file (MB)", l.maxFileMb, "maxFileMb", "e.g. 500")}
          {field("lim-total", "Whole package (MB)", l.maxTotalMb, "maxTotalMb", "e.g. 4096")}
          {field("lim-count", "Most files", l.maxFiles, "maxFiles", "")}
          {field("lim-name-len", "Longest file name", l.maxNameLength, "maxNameLength", "characters")}
          <div style={{ flex: "1 1 130px" }}>
            <label htmlFor="lim-zip" style={label}>ZIP</label>
            <select id="lim-zip" value={l.zip} onChange={e => setLimit({ zip: e.target.value as LimitsDraft["zip"] })} style={input}>
              <option value="">Not stated</option><option value="yes">Allowed</option><option value="no">Not allowed</option>
            </select>
          </div>
          <button type="button" onClick={() => set({ limits: EMPTY_LIMITS })} style={{ ...btn, padding: "6px 12px", fontSize: 12.5 }}>Clear</button>
        </div>
      )}
      {notes.map(n => <p key={n} style={{ fontSize: 12, color: "var(--hf-warning-text-deep)", margin: "10px 0 0" }}>{n}</p>)}
    </section>
  )
}

// ---- version history

function Versions({ packages, loading }: { packages: TenderPackage[]; loading: boolean }) {
  const [error, setError] = useState("")
  const [open, setOpen] = useState<string | null>(null)
  const th: React.CSSProperties = { textAlign: "left", fontSize: 10.5, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--hf-text-faint)", padding: "8px 10px" }
  const td: React.CSSProperties = { padding: "10px", fontSize: 13, color: "var(--hf-text)", verticalAlign: "middle" }
  return (
    <section aria-label="Package versions" style={{ ...card, marginTop: 16 }}>
      <h3 style={{ ...h3, marginBottom: 12 }}><History size={15} aria-hidden="true" /> Versions</h3>
      {loading && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>Loading…</p>}
      {!loading && packages.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>No package has been built yet. Each build is kept as its own version, with a record of exactly what was in it.</p>}
      {error && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{error}</p>}
      {packages.length > 0 && (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 640 }}>
            <thead><tr><th style={th}>Version</th><th style={th}>Status</th><th style={th}>Size</th><th style={th}>Built</th><th style={th}>Fingerprint</th><th style={th} aria-label="Actions" /></tr></thead>
            <tbody>
              {packages.map(p => (
                <Fragment key={p.id}>
                  <tr style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                    <td style={{ ...td, fontWeight: 700 }}>v{p.versionNo}</td>
                    <td style={td}>
                      <span style={{ fontSize: 11.5, fontWeight: 700, padding: "2px 9px", borderRadius: 999, background: p.submissionReady ? "var(--hf-success-soft-strong)" : "var(--hf-warning-soft-strong)", color: p.submissionReady ? "var(--hf-success-text-strong)" : "var(--hf-warning-text-deep)" }}>
                        {p.submissionReady ? "Ready to submit" : "Draft"}
                      </span>
                      {p.includesPricing && <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginLeft: 8 }}>with pricing</span>}
                      {p.stale && <span style={{ marginLeft: 8 }}><Chip tone="warn" title={(p.staleReasons ?? []).join(". ")}>Out of date</Chip></span>}
                    </td>
                    <td style={td}>{fmtSize(p.sizeBytes)}{p.pageCount ? ` · ${p.pageCount} pages` : ""}</td>
                    <td style={td}>{fmtWhen(p.createdAt)}{p.createdByName ? <span style={{ color: "var(--hf-text-faint)" }}> · {p.createdByName}</span> : null}</td>
                    <td style={{ ...td, fontFamily: "ui-monospace, monospace", fontSize: 12, color: "var(--hf-text-muted)" }} title={p.packageHash}>{shortHash(p.packageHash)}</td>
                    <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                      <button type="button" onClick={() => setOpen(open === p.id ? null : p.id)} aria-expanded={open === p.id} style={{ ...btn, padding: "5px 10px", fontSize: 12, marginRight: 6 }}>{open === p.id ? "Hide files" : `${p.files.length} ${p.files.length === 1 ? "file" : "files"}`}</button>
                      <button type="button" onClick={async () => { setError(""); try { await downloadPackage(p.id, p.fileName) } catch (e) { setError(apiMessage(e)) } }} style={{ ...btn, padding: "5px 10px", fontSize: 12 }}><Download size={13} aria-hidden="true" /> Download</button>
                    </td>
                  </tr>
                  {open === p.id && (
                    <tr>
                      <td colSpan={6} style={{ padding: "0 10px 12px", background: "var(--hf-surface-muted)" }}>
                        <ol style={{ margin: 0, padding: "8px 0 0 18px", fontSize: 12.5, color: "var(--hf-text-secondary)" }}>
                          {p.files.map(f => <li key={f.position}>{f.fileName} <span style={{ color: "var(--hf-text-faint)" }}>· {sourceText(f.source)} · {fmtSize(f.sizeBytes)}{f.pages ? ` · ${f.pages} pages` : ""}</span></li>)}
                        </ol>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
