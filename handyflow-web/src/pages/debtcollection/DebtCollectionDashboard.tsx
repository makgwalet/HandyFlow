// src/pages/debtcollection/DebtCollectionDashboard.tsx
//
// Real dashboard endpoints: /dashboard/summary (with previous-period comparison),
// /aging, /top-debtors, /trend and /recovery. Recovery needs MANAGE; if the user
// lacks it that card simply shows nothing.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle, ArrowRight, CalendarClock, Landmark, Plus, TrendingDown, Wallet, CheckCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import {
  PERIODS, changeText, dayText, donutArcs, linePoints, money, moneyShort, objOf, overdueText, periodRange, rowsOf,
  type Aging, type Period, type Recovery, type Summary, type TopDebtor, type TrendPoint,
} from "./collections.logic"
import { card, pill } from "./ui"

type Tab = "dashboard" | "cases"
const get = (path: string, from?: string, to?: string) => apiClient.get(`/api/v1/debtcollection/dashboard/${path}${from ? `?from=${from}&to=${to}` : ""}`)

function Kpi({ label, value, icon: Icon, change, tone = "var(--hf-orange-text-strong)", onClick }: { label: string; value: string | number; icon: any; change?: { text: string; up: boolean } | null; tone?: string; onClick?: () => void }) {
  return (
    <div onClick={onClick} style={{ ...card, cursor: onClick ? "pointer" : "default" }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)" }}>{label}</span>
        <Icon size={16} style={{ color: tone }} />
      </div>
      <div style={{ fontSize: 24, fontWeight: 800, color: tone }}>{value}</div>
      {change && <div style={{ fontSize: 12, marginTop: 4, color: change.up ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>{change.text}</div>}
    </div>
  )
}

export default function DebtCollectionDashboard({ onNavigate }: { onNavigate: (t: Tab) => void }) {
  const [period, setPeriod] = useState<Period>("month")
  const [from, to] = periodRange(period, new Date())

  const summary = useQuery<Summary>({ queryKey: ["dc-dash-summary", from, to], queryFn: async () => objOf<Summary>(await get("summary", from, to)) })
  const aging = useQuery<Aging>({ queryKey: ["dc-dash-aging"], queryFn: async () => objOf<Aging>(await get("aging")) })
  const debtors = useQuery<TopDebtor[]>({ queryKey: ["dc-dash-top"], queryFn: async () => rowsOf<TopDebtor>(await get("top-debtors?limit=8")) })
  const trend = useQuery<TrendPoint[]>({ queryKey: ["dc-dash-trend", from, to], queryFn: async () => rowsOf<TrendPoint>(await get("trend", from, to)) })
  const recovery = useQuery<Recovery>({ queryKey: ["dc-dash-recovery", from, to], retry: false, queryFn: async () => objOf<Recovery>(await get("recovery", from, to)) })

  const s = summary.data
  const arcs = donutArcs(aging.data?.buckets ?? [], 52)
  const points = trend.data ?? []
  const [outLine, recLine] = linePoints([points.map(p => p.outstanding), points.map(p => p.recoveredNet)], 520, 150)

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16, alignItems: "center", flexWrap: "wrap" }}>
        {PERIODS.map(p => <button key={p.id} style={pill(period === p.id)} onClick={() => setPeriod(p.id)}>{p.label}</button>)}
        <button onClick={() => onNavigate("cases")} style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
          <Plus size={14} /> Open a case
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(200px,1fr))", gap: 14, marginBottom: 18 }}>
        <Kpi label="Total outstanding" value={money(s?.totalOutstanding)} icon={TrendingDown} onClick={() => onNavigate("cases")} />
        <Kpi label="Active cases" value={s?.activeCases ?? "—"} icon={Landmark} onClick={() => onNavigate("cases")} />
        <Kpi label="Cases opened" value={s?.current.casesOpened ?? "—"} icon={Plus} change={changeText(s?.current.casesOpened, s?.previous.casesOpened)} />
        <Kpi label="Recovered (net)" value={recovery.data ? money(recovery.data.netRecovered) : "—"} icon={Wallet} tone="var(--hf-success-text-strong)" />
        <Kpi label="Overdue follow-ups" value={s?.overdueWorkItems ?? "—"} icon={AlertTriangle} tone={s && s.overdueWorkItems > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)"} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0,2fr) minmax(0,1fr)", gap: 14, marginBottom: 14 }}>
        <div style={card}>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 10, color: "var(--hf-text)" }}>Outstanding and recovered</div>
          {points.length < 2 ? <div style={{ color: "var(--hf-text-faint)", fontSize: 13, padding: "30px 0" }}>The trend fills in as daily snapshots build up.</div> : (
            <>
              <svg viewBox="0 0 520 150" width="100%" role="img" aria-label="Outstanding and recovered trend">
                <polyline fill="none" stroke="#f97316" strokeWidth="2.5" points={outLine} />
                <polyline fill="none" stroke="#22c55e" strokeWidth="2.5" points={recLine} />
              </svg>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "var(--hf-text-faint)" }}>
                <span>{dayText(points[0].date)}</span>
                <span><span style={{ color: "#f97316" }}>●</span> Outstanding {moneyShort(points[points.length - 1].outstanding)} &nbsp; <span style={{ color: "#22c55e" }}>●</span> Recovered {moneyShort(points[points.length - 1].recoveredNet)}</span>
                <span>{dayText(points[points.length - 1].date)}</span>
              </div>
            </>
          )}
        </div>

        <div style={card}>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 10, color: "var(--hf-text)" }}>Aging of what is owed</div>
          {arcs.length === 0 ? <div style={{ color: "var(--hf-text-faint)", fontSize: 13, padding: "30px 0" }}>Nothing outstanding on active cases.</div> : (
            <div style={{ display: "flex", gap: 14, alignItems: "center" }}>
              <svg viewBox="0 0 120 120" width={120} height={120} role="img" aria-label="Aging">
                <g transform="rotate(-90 60 60)">
                  {arcs.map(a => <circle key={a.label} cx="60" cy="60" r="52" fill="none" stroke={a.colour} strokeWidth="14" strokeDasharray={`${a.dash} ${a.gap}`} strokeDashoffset={a.offset} />)}
                </g>
                <text x="60" y="64" textAnchor="middle" fontSize="13" fontWeight="700" fill="currentColor">{moneyShort(aging.data!.totalOutstanding)}</text>
              </svg>
              <div style={{ fontSize: 12, display: "flex", flexDirection: "column", gap: 4 }}>
                {arcs.map(a => <div key={a.label}><span style={{ color: a.colour }}>●</span> {a.label} · {a.share}%</div>)}
              </div>
            </div>
          )}
        </div>
      </div>

      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
          <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>Top debtors</span>
          <button onClick={() => onNavigate("cases")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-orange-text-strong)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>All cases <ArrowRight size={13} /></button>
        </div>
        {(debtors.data ?? []).length === 0 ? (
          <div style={{ textAlign: "center", padding: "26px 0", color: "var(--hf-text-faint)", fontSize: 13 }}><CheckCircle size={26} style={{ marginBottom: 6, opacity: 0.5 }} /><div>No open debtors.</div></div>
        ) : (debtors.data ?? []).map(d => (
          <div key={d.caseId} onClick={() => onNavigate("cases")} style={{ display: "flex", alignItems: "center", gap: 12, padding: "9px 0", borderTop: "1px solid var(--hf-border)", cursor: "pointer" }}>
            <CalendarClock size={15} style={{ color: "var(--hf-orange-text-strong)" }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)" }}>{d.debtorName} <span style={{ color: "var(--hf-text-faint)", fontWeight: 400 }}>{d.caseNumber}</span></div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{d.assignedToUserName ?? "Unassigned"}{d.nextActionDate ? ` · next action ${dayText(d.nextActionDate)}` : ""}</div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontWeight: 700, fontSize: 13 }}>{money(d.outstanding)}</div>
              <div style={{ fontSize: 11, color: d.maxDaysOverdue > 60 ? "var(--hf-danger-text)" : "var(--hf-text-faint)" }}>{overdueText(d.maxDaysOverdue)}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
