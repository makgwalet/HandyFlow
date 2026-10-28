// src/pages/hr/ReportCharts.tsx
//
// Charts for the four HR reports. They draw only the figures the report already holds (groups hidden for being too small are left out),
// and every figure is also in the tables underneath, so a chart is never the only place a number appears.
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { Block } from "./ReportParts"
import {
  checkBars, costBars, groupBars, leaveBars, movementBars, overtimeBars, runBars, type Bar as BarData,
} from "./reports.compare"
import { rand } from "./reports.logic"
import type { Compliance, PayrollReport, TimeReport, Workforce } from "./reports.shared"

const PRIMARY = "var(--hf-primary)"
const SECOND = "var(--hf-chart-2, #7c8aa5)"
const THIRD = "var(--hf-chart-3, #c9a227)"

function Horizontal({ title, bars, unit }: { title: string; bars: BarData[]; unit?: "money" }) {
  if (bars.length === 0) return null
  const fmt = (v: unknown) => (unit === "money" ? rand(Number(v)) : String(v))
  return (
    <Block title={title}>
      <div role="img" aria-label={`${title}: ${bars.map(b => `${b.label} ${fmt(b.value)}`).join(", ")}`} style={{ width: "100%", height: Math.max(120, bars.length * 34 + 30) }}>
        <ResponsiveContainer>
          <BarChart data={bars} layout="vertical" margin={{ left: 8, right: 24 }}>
            <CartesianGrid horizontal={false} strokeDasharray="3 3" />
            <XAxis type="number" allowDecimals={false} tickFormatter={fmt} />
            <YAxis type="category" dataKey="label" width={140} />
            <Tooltip formatter={(v: unknown) => fmt(v)} />
            <Bar dataKey="value" fill={PRIMARY} radius={[0, 4, 4, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </Block>
  )
}

export const WorkforceCharts = ({ w }: { w: Workforce }) => (
  <>
    <Horizontal title="Headcount movement" bars={movementBars(w)} />
    <Horizontal title="People by department" bars={groupBars(w.byDepartment)} />
    <Horizontal title="Why people left" bars={groupBars(w.leaversByReason)} />
  </>
)

export const TimeCharts = ({ t }: { t: TimeReport }) => (
  <>
    <Horizontal title="Approved leave days by type" bars={leaveBars(t)} />
    <Horizontal title="Approved overtime hours by kind" bars={overtimeBars(t)} />
  </>
)

export function PayrollCharts({ p }: { p: PayrollReport }) {
  const runs = runBars(p)
  return (
    <>
      {runs.length > 0 && (
        <Block title="Each pay run">
          <div role="img" aria-label={`Each pay run: ${runs.map(r => `${r.label} gross ${rand(r.gross)}, net ${rand(r.net)}, employer cost ${rand(r.employerCost)}`).join("; ")}`}
            style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={runs} margin={{ left: 8, right: 16 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis tickFormatter={(v: number) => `R${Math.round(v / 1000)}k`} />
                <Tooltip formatter={(v: unknown) => rand(Number(v))} />
                <Legend />
                <Bar dataKey="gross" name="Gross" fill={PRIMARY} isAnimationActive={false} />
                <Bar dataKey="net" name="Net" fill={SECOND} isAnimationActive={false} />
                <Bar dataKey="employerCost" name="Employer cost" fill={THIRD} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Block>
      )}
      <Horizontal title="Employer cost by department" bars={costBars(p)} unit="money" />
    </>
  )
}

export const ComplianceCharts = ({ c }: { c: Compliance }) => (
  <>
    <Horizontal title="Items to fix, by check" bars={checkBars(c)} />
    {c.employeeRelations && <Horizontal title="Employee relations cases opened, by type" bars={groupBars(c.employeeRelations.byType)} />}
    {c.employeeRelations && <Horizontal title="Case outcomes" bars={groupBars(c.employeeRelations.outcomes)} />}
  </>
)
