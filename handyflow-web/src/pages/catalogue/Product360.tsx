// src/pages/catalogue/Product360.tsx — one item in one place: header, completeness, and tabs for details, attributes, variants, pricing and history.
// Changing things still happens in the existing dialogs (variants, pricing); this view shows the whole picture and what is outstanding.
import { useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, Modal, StatusBadge } from "./CatalogueKit"
import { linkBtn, primaryBtn } from "./catalogue.styles"
import { DecisionModal } from "./ItemApprovalsPanel"
import ChannelsTab from "./ChannelsTab"
import MediaTab from "./MediaTab"
import PackageTab, { UsedInList } from "./PackageTab"
import PricingModal from "./PricingModal"
import VariantsModal from "./VariantsModal"
import {
  APPROVAL_LABEL, actionText, errorMessage, marginText, outstanding, rand, ringTone, typeLabel,
  type HistoryLine, type ItemApproval, type ItemOverview, type ItemPricing, type PageOf, type VariantsView,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", borderBottom: "1px solid var(--hf-border-subtle)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)" }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const banner: React.CSSProperties = { display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "10px 14px", marginBottom: 14, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border-subtle)", borderRadius: 10, fontSize: 13 }

type Tab = "overview" | "attributes" | "media" | "package" | "channels" | "variants" | "pricing" | "history"

export function CompletenessRing({ score }: { score: number }) {
  const r = 30, c = 2 * Math.PI * r
  return (
    <div role="img" aria-label={`Completeness ${score} percent`} style={{ position: "relative", width: 76, height: 76, flexShrink: 0 }}>
      <svg width="76" height="76" viewBox="0 0 76 76" aria-hidden="true">
        <circle cx="38" cy="38" r={r} fill="none" stroke="var(--hf-border-subtle)" strokeWidth="7" />
        <circle cx="38" cy="38" r={r} fill="none" stroke={ringTone(score)} strokeWidth="7" strokeLinecap="round" strokeDasharray={`${(c * score) / 100} ${c}`} transform="rotate(-90 38 38)" />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{score}%</div>
    </div>
  )
}

export default function Product360({ itemId, onClose, onChanged }: { itemId: string; onClose: () => void; onChanged: () => void }) {
  const canWrite = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_WRITE"])
  const canSeeCost = usePermissions(["CATALOGUE_PRICE_READ"])
  const canWriteCost = usePermissions(["CATALOGUE_PRICE_WRITE"])
  const canDecide = usePermissions(["CATALOGUE_APPROVE"])
  const canPublish = usePermissions(["CATALOGUE_PUBLISH", "SETTINGS_MANAGE"])
  const [tab, setTab] = useState<Tab>("overview")
  const [dialog, setDialog] = useState<"variants" | "pricing" | null>(null)
  const [deciding, setDeciding] = useState<{ a: ItemApproval; kind: "approve" | "reject" } | null>(null)
  const [error, setError] = useState("")
  const ov = useQuery<ItemOverview>({ queryKey: ["catalogue", "overview", itemId], queryFn: async () => inner<ItemOverview>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/overview`)) })
  const o = ov.data
  const item = o?.item
  const hasVariants = (item?.variantCount ?? 0) > 0
  const tabs: { id: Tab; label: string }[] = [
    { id: "overview", label: "Overview" }, { id: "attributes", label: "Attributes" }, { id: "media", label: "Media" },
    ...(item?.itemType === "PACKAGE" ? [{ id: "package" as Tab, label: "Package" }] : []),
    ...(!item?.parentId ? [{ id: "variants" as Tab, label: "Variants" }, { id: "channels" as Tab, label: "Channels" }] : []),
    ...(canSeeCost ? [{ id: "pricing" as Tab, label: "Pricing" }] : []),
    { id: "history", label: "History" },
  ]
  const changed = () => { ov.refetch(); onChanged() }
  const submit = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/catalogue/items/${itemId}/approval`, {}),
    onSuccess: () => { setError(""); changed() }, onError: e => setError(errorMessage(e, "Couldn't submit the item.")),
  })
  const pending = useQuery<ItemApproval | null>({
    queryKey: ["catalogue", "item-approval", itemId, o?.pendingApprovalId ?? null], enabled: !!o?.pendingApprovalId,
    queryFn: async () => inner<ItemApproval | null>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/approval`)),
  })
  const canSubmit = !!o && o.approvalRequired && canWrite && item?.status === "DRAFT" && !item.parentId && !o.pendingApprovalId

  return (
    <Modal title={item ? `${item.name}` : "Product"} onClose={onClose} wide>
      {ov.isLoading && <p role="status">Loading…</p>}
      {ov.isError && <ErrorBanner message={errorMessage(ov.error, "Couldn't load this item.")} />}
      {o && item && (
        <>
          <div style={{ display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap", marginBottom: 16 }}>
            <CompletenessRing score={o.completeness.score} />
            <div style={{ flex: 1, minWidth: 220 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <span style={{ fontFamily: "monospace", fontSize: 13, color: "var(--hf-text-muted)" }}>{item.sku ?? "No SKU"}</span>
                <StatusBadge status={item.status} />
                <span style={muted}>{typeLabel(item.itemType)}</span>
              </div>
              <div style={{ ...muted, marginTop: 4 }}>
                {item.parentId && o.parentName ? `Variant of ${o.parentName} · ${item.variantLabel ?? ""}` : o.categoryPath ?? "No category"}
              </div>
              <div style={{ ...muted, marginTop: 4 }}>Approval: {APPROVAL_LABEL[o.approvalState]}</div>
            </div>
          </div>

          {error && <ErrorBanner message={error} />}
          {canSubmit && (
            <div role="region" aria-label="Approval" style={{ ...banner }}>
              <span>This draft must be approved before it can be sold.</span>
              <button type="button" style={primaryBtn} disabled={submit.isPending} onClick={() => submit.mutate()}>{submit.isPending ? "Submitting…" : "Submit for approval"}</button>
            </div>
          )}
          {o.pendingApprovalId && (
            <div role="region" aria-label="Approval" style={{ ...banner }}>
              <span>Waiting for approval{pending.data?.note ? `: ${pending.data.note}` : "."}</span>
              {canDecide && pending.data && <span style={{ display: "flex", gap: 6 }}>
                <button type="button" style={primaryBtn} onClick={() => setDeciding({ a: pending.data as ItemApproval, kind: "approve" })}>Approve</button>
                <button type="button" style={linkBtn} onClick={() => setDeciding({ a: pending.data as ItemApproval, kind: "reject" })}>Reject</button>
              </span>}
            </div>
          )}
          <div role="tablist" aria-label="Item sections" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border-subtle)", marginBottom: 16, overflowX: "auto" }}>
            {tabs.map(t => (
              <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
                style={{ padding: "9px 14px", background: "none", border: "none", borderBottom: tab === t.id ? "2px solid var(--hf-primary)" : "2px solid transparent", fontSize: 13, fontWeight: 600, cursor: "pointer", color: tab === t.id ? "var(--hf-text)" : "var(--hf-text-muted)", whiteSpace: "nowrap" }}>{t.label}</button>
            ))}
          </div>

          {tab === "overview" && <><OverviewTab o={o} />{item.itemType !== "PACKAGE" && <UsedInList itemId={itemId} />}</>}
          {tab === "attributes" && <AttributesTab o={o} />}
          {tab === "media" && <MediaTab itemId={itemId} canWrite={canWrite} />}
          {tab === "channels" && <ChannelsTab itemId={itemId} canPublish={canPublish} />}
          {tab === "package" && <PackageTab itemId={itemId} canWrite={canWrite} onChanged={changed} />}
          {tab === "variants" && <VariantsTab itemId={itemId} canWrite={canWrite} hasVariants={hasVariants} onManage={() => setDialog("variants")} />}
          {tab === "pricing" && <PricingTab itemId={itemId} hasVariants={hasVariants} canWrite={canWriteCost} onManage={() => setDialog("pricing")} />}
          {tab === "history" && <HistoryTab itemId={itemId} />}

          {deciding && <DecisionModal approval={deciding.a} kind={deciding.kind} onClose={() => setDeciding(null)} onDone={() => { setDeciding(null); changed() }} />}
          {dialog === "variants" && <VariantsModal item={item} canWrite={canWrite} canSeeCost={canSeeCost} canWriteCost={canWriteCost} onClose={() => setDialog(null)} onChanged={changed} />}
          {dialog === "pricing" && <PricingModal item={item} canWrite={canWriteCost} onClose={() => setDialog(null)} onChanged={changed} />}
        </>
      )}
    </Modal>
  )
}

function OverviewTab({ o }: { o: ItemOverview }) {
  const issues = outstanding(o)
  const i = o.item
  const rows: [string, string][] = [
    ["Description", i.description || "—"], ["Brand", i.brand || "—"], ["Barcode", i.barcode || "—"], ["Unit", i.unit],
    ["Price", rand(i.defaultPrice)], ["VAT", i.vatExempt ? "Exempt" : `${i.vatRate}%`],
    ...(i.costPrice !== undefined && i.costPrice !== null ? [["Cost", rand(i.costPrice)] as [string, string], ["Margin", marginText(i.marginPercent)] as [string, string]] : []),
  ]
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", gap: 20 }}>
      <section aria-label="Details">
        <dl style={{ margin: 0 }}>
          {rows.map(([k, v]) => (
            <div key={k} style={{ display: "flex", gap: 12, padding: "7px 0", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
              <dt style={{ width: 100, color: "var(--hf-text-faint)", flexShrink: 0 }}>{k}</dt><dd style={{ margin: 0, color: "var(--hf-text)" }}>{v}</dd>
            </div>
          ))}
        </dl>
      </section>
      <section aria-label="Outstanding">
        <h4 style={{ margin: "0 0 8px", fontSize: 13, color: "var(--hf-text)" }}>Outstanding</h4>
        {issues.length === 0
          ? <p style={muted}>Nothing outstanding. This record is complete.</p>
          : <ul style={{ margin: 0, paddingLeft: 18, ...muted }}>{issues.map(t => <li key={t} style={{ marginBottom: 4 }}>{t}</li>)}</ul>}
        {o.openPriceChanges > 0 && <p style={{ ...muted, marginTop: 10 }}>{o.openPriceChanges} price change{o.openPriceChanges === 1 ? "" : "s"} not yet in force.</p>}
      </section>
    </div>
  )
}

function AttributesTab({ o }: { o: ItemOverview }) {
  const values = o.item.attributes ?? []
  return (
    <div>
      {values.length === 0 && o.missingAttributes.length === 0 && <p style={muted}>No attributes apply to this item.</p>}
      {values.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <tbody>{values.map(a => <tr key={a.definitionId}><td style={{ ...td, color: "var(--hf-text-faint)", width: 180 }}>{a.label}</td><td style={td}>{a.value || "—"}</td></tr>)}</tbody>
        </table>
      )}
      {o.missingAttributes.length > 0 && <p style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 12 }}>Required and empty: {o.missingAttributes.join(", ")}</p>}
    </div>
  )
}

function VariantsTab({ itemId, canWrite, hasVariants, onManage }: { itemId: string; canWrite: boolean; hasVariants: boolean; onManage: () => void }) {
  const q = useQuery<VariantsView>({ queryKey: ["catalogue", "variants", itemId], queryFn: async () => inner<VariantsView>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/variants`)) })
  return (
    <div>
      {q.isLoading && <p role="status">Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the variants.")} />}
      {q.data && q.data.variants.length === 0 && <p style={muted}>No variants. This item is sold as it is.</p>}
      {q.data && q.data.variants.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 12 }}>
          <thead><tr><th style={th}>Variant</th><th style={th}>SKU</th><th style={th}>Price</th><th style={th}>Status</th></tr></thead>
          <tbody>{q.data.variants.map(v => <tr key={v.id}><td style={td}>{v.label}</td><td style={td}>{v.sku ?? "—"}</td><td style={td}>{rand(v.defaultPrice)}</td><td style={td}><StatusBadge status={v.status} /></td></tr>)}</tbody>
        </table>
      )}
      {(canWrite || hasVariants) && <button type="button" style={primaryBtn} onClick={onManage}>Manage variants</button>}
    </div>
  )
}

function PricingTab({ itemId, hasVariants, canWrite, onManage }: { itemId: string; hasVariants: boolean; canWrite: boolean; onManage: () => void }) {
  const q = useQuery<ItemPricing>({ queryKey: ["catalogue", "pricing", itemId], queryFn: async () => inner<ItemPricing>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/pricing`)), enabled: !hasVariants })
  if (hasVariants) return <p style={muted}>This item is sold through its variants. Each variant has its own prices.</p>
  return (
    <div>
      {q.isLoading && <p role="status">Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the prices.")} />}
      {q.data && (
        <>
          <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 12 }}>
            <thead><tr><th style={th}>List</th><th style={th}>Price</th><th style={th}>Margin</th></tr></thead>
            <tbody>{q.data.lists.map(l => <tr key={l.priceListId}><td style={td}>{l.name}{l.open.length > 0 && <span style={{ ...muted, marginLeft: 8 }}>change pending</span>}</td><td style={td}>{rand(l.price)}</td><td style={td}>{marginText(l.marginPercent)}</td></tr>)}</tbody>
          </table>
          <p style={muted}>Minimum price: {rand(q.data.minPrice)}. {q.data.approvalRequired ? "Price changes need approval." : "Price changes need no approval."}</p>
          <button type="button" style={canWrite ? primaryBtn : linkBtn} onClick={onManage}>{canWrite ? "Manage pricing" : "Open pricing"}</button>
        </>
      )}
    </div>
  )
}

function HistoryTab({ itemId }: { itemId: string }) {
  const h = useQuery<PageOf<HistoryLine>>({ queryKey: ["catalogue", "history", itemId], queryFn: async () => inner<PageOf<HistoryLine>>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/history`, { params: { size: 50 } })) })
  return (
    <div>
      {h.isLoading && <p role="status">Loading…</p>}
      {h.isError && <ErrorBanner message="Couldn't load the history." />}
      {h.data && h.data.content.length === 0 && <p style={muted}>Nothing recorded yet.</p>}
      {h.data && h.data.content.length > 0 && (
        <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {h.data.content.map(l => (
            <li key={l.id} style={{ padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}><strong>{actionText(l.action)}</strong><span style={{ color: "var(--hf-text-faint)" }}>{new Date(l.at).toLocaleString("en-ZA")}</span></div>
              {l.detail && <div style={muted}>{l.detail}</div>}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
