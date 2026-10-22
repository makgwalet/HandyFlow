// The four reports. Each takes what the server worked out and lays it out; nothing is calculated here.
import { fmtDate, words } from "./er.logic"
import { pct, rand } from "./reports.logic"
import { td, type Compliance, type PayrollReport, type TimeReport, type Workforce } from "./reports.shared"
import { Block, Breakdown, Head, Notes, Stat, StatRow } from "./ReportParts"
import { Notice } from "./AttendanceParts"

const num = (n: number | null | undefined) => (n === null || n === undefined ? "–" : n.toLocaleString("en-ZA"))
const right = { ...td, textAlign: "right" as const }

export function WorkforceView({ w }: { w: Workforce }) {
  return (
    <div>
      <StatRow>
        <Stat label="Headcount at start" value={num(w.opening)} />
        <Stat label="Joiners" value={num(w.joiners)} />
        <Stat label="Leavers" value={num(w.leavers)} />
        <Stat label="Headcount at end" value={num(w.closing)} />
        <Stat label="Turnover for the period" value={pct(w.turnoverPercent)} hint={`${pct(w.annualisedTurnoverPercent)} a year at this rate`} />
      </StatRow>
      <Breakdown title="By department" groups={w.byDepartment} />
      <Breakdown title="By employment type" groups={w.byEmploymentType} />
      <Breakdown title="By length of service" groups={w.tenure} />
      <Breakdown title="By age" groups={w.age} />
      <Breakdown title="Why people left" groups={w.leaversByReason} empty="Nobody left in this period." />
      {w.demographicsShown ? (
        <>
          <Breakdown title="Gender" groups={w.gender} />
          <Breakdown title="Race" groups={w.race} />
          <Breakdown title="Disability" groups={w.disability} />
        </>
      ) : <Notice kind="ok">Gender, race and disability figures are shown only to people with access to sensitive records.</Notice>}
      <Notes notes={w.notes} />
    </div>
  )
}

export function TimeView({ t }: { t: TimeReport }) {
  return (
    <div>
      <StatRow>
        <Stat label="Leave waiting for a decision" value={num(t.leavePending)} hint={t.leavePendingStale > 0 ? `${t.leavePendingStale} waiting more than 14 days` : undefined} />
        <Stat label="Sick leave days" value={num(t.sickDays)} hint={`${num(t.sickEmployees)} employees`} />
        <Stat label="Overtime waiting" value={num(t.overtimePending)} />
      </StatRow>
      <Block title="Leave taken">
        {t.leave.length === 0 ? <div style={{ fontSize: 13 }}>No approved leave started in this period.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <Head cols={["Type", "Requests", "Days"]} right={[1, 2]} />
            <tbody>{t.leave.map(l => <tr key={l.leaveType}><td style={td}>{words(l.leaveType)}</td><td style={right}>{num(l.requests)}</td><td style={right}>{num(l.days)}</td></tr>)}</tbody>
          </table>
        )}
      </Block>
      <Block title="Attendance">
        {t.attendance ? (
          <>
            <StatRow>
              <Stat label="Days present" value={num(t.attendance.presentDays)} />
              <Stat label="Days late" value={num(t.attendance.lateDays)} />
              <Stat label="Days absent" value={num(t.attendance.absentDays)} />
              <Stat label="Days on leave" value={num(t.attendance.leaveDays)} />
              <Stat label="Hours worked" value={num(t.attendance.workedHours)} hint={`${num(t.attendance.overtimeHours)} overtime`} />
            </StatRow>
            {t.attendance.mostAbsent.length > 0 && (
              <table style={{ width: "100%", borderCollapse: "collapse" }} aria-label="Most absent">
                <Head cols={["Employee", "Department", "Absent", "Late"]} right={[2, 3]} />
                <tbody>{t.attendance.mostAbsent.map(o => (
                  <tr key={o.employeeNumber}><td style={td}>{o.employeeName} · {o.employeeNumber}</td><td style={td}>{o.department ?? "–"}</td><td style={right}>{o.absentDays}</td><td style={right}>{o.lateDays}</td></tr>))}
                </tbody>
              </table>
            )}
          </>
        ) : <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{t.attendanceNote}</div>}
      </Block>
      <Block title="Approved overtime">
        {t.overtimeByKind.length === 0 ? <div style={{ fontSize: 13 }}>No approved overtime in this period.</div> : (
          <>
            <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 12 }} aria-label="Overtime by kind">
              <Head cols={["Kind", "Claims", "Hours", "Amount"]} right={[1, 2, 3]} />
              <tbody>{t.overtimeByKind.map(l => <tr key={l.label}><td style={td}>{words(l.label)}</td><td style={right}>{num(l.count)}</td><td style={right}>{num(l.hours)}</td><td style={right}>{rand(l.amount)}</td></tr>)}</tbody>
            </table>
            <table style={{ width: "100%", borderCollapse: "collapse" }} aria-label="Overtime by department">
              <Head cols={["Department", "Claims", "Hours", "Amount"]} right={[1, 2, 3]} />
              <tbody>{t.overtimeByDepartment.map(l => <tr key={l.label}><td style={td}>{l.label}</td><td style={right}>{num(l.count)}</td><td style={right}>{num(l.hours)}</td><td style={right}>{rand(l.amount)}</td></tr>)}</tbody>
            </table>
          </>
        )}
      </Block>
      <Notes notes={t.notes} />
    </div>
  )
}

export function PayrollView({ p }: { p: PayrollReport }) {
  const tt = p.totals
  const uncounted = p.runs.filter(r => !r.counted).length
  return (
    <div>
      <StatRow>
        <Stat label="Gross pay" value={rand(tt.gross)} hint={`${tt.runs} finished run${tt.runs === 1 ? "" : "s"}`} />
        <Stat label="Employer cost" value={rand(tt.employerCost)} hint="Gross plus employer UIF and SDL" />
        <Stat label="PAYE" value={rand(tt.paye)} />
        <Stat label="UIF" value={rand(tt.uifEmployee + tt.uifEmployer)} hint="Employee and employer" />
        <Stat label="SDL" value={rand(tt.sdl)} />
        <Stat label="Net pay" value={rand(tt.net)} />
      </StatRow>
      {uncounted > 0 && <Notice kind="error">{uncounted} pay run{uncounted === 1 ? " is" : "s are"} not finished and not in the totals above.</Notice>}
      <Block title="Pay runs">
        {p.runs.length === 0 ? <div style={{ fontSize: 13 }}>No pay runs with a pay date in this period.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <Head cols={["Run", "Pay date", "Status", "Employees", "Gross", "PAYE", "Net", "Employer cost"]} right={[3, 4, 5, 6, 7]} />
            <tbody>{p.runs.map(r => (
              <tr key={r.number} style={r.counted ? undefined : { color: "var(--hf-text-muted)" }}>
                <td style={td}>{r.number}</td><td style={td}>{fmtDate(r.payDate)}</td><td style={td}>{words(r.status)}{r.counted ? "" : " (not counted)"}</td>
                <td style={right}>{r.employees}</td><td style={right}>{rand(r.gross)}</td><td style={right}>{rand(r.paye)}</td><td style={right}>{rand(r.net)}</td><td style={right}>{rand(r.employerCost)}</td>
              </tr>))}
            </tbody>
          </table>
        )}
      </Block>
      <Block title="Employer cost by department">
        {p.byDepartment.length === 0 ? <div style={{ fontSize: 13 }}>Nothing to show.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <Head cols={["Department", "Employees", "Cost"]} right={[1, 2]} />
            <tbody>{p.byDepartment.map(l => <tr key={l.label}><td style={td}>{l.label}</td><td style={right}>{num(l.count)}</td><td style={right}>{rand(l.amount)}</td></tr>)}</tbody>
          </table>
        )}
      </Block>
      <Block title="EMP201 returns">
        {p.emp201.length === 0 ? <div style={{ fontSize: 13 }}>No EMP201 returns for periods ending in this period.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <Head cols={["Period end", "Due", "PAYE", "UIF", "SDL", "Payable", "Status"]} right={[2, 3, 4, 5]} />
            <tbody>{p.emp201.map(e => (
              <tr key={e.periodEnd}>
                <td style={td}>{fmtDate(e.periodEnd)}</td><td style={td}>{fmtDate(e.dueDate)}</td>
                <td style={right}>{rand(e.paye)}</td><td style={right}>{rand(e.uif)}</td><td style={right}>{rand(e.sdl)}</td><td style={right}>{rand(e.payable)}</td>
                <td style={{ ...td, color: e.overdue ? "var(--hf-danger-text)" : undefined, fontWeight: e.overdue ? 700 : undefined }}>{words(e.status)}{e.overdue ? " – overdue" : ""}</td>
              </tr>))}
            </tbody>
          </table>
        )}
      </Block>
      <Notes notes={p.notes} />
    </div>
  )
}

export function ComplianceView({ c }: { c: Compliance }) {
  const er = c.employeeRelations
  return (
    <div>
      <Block title="Records and follow-up">
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <Head cols={["Check", "Count", "Why it matters"]} right={[1]} />
          <tbody>{c.checks.map(k => (
            <tr key={k.code}>
              <td style={td}>
                {k.title}
                {k.people.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{k.people.join(", ")}</div>}
              </td>
              <td style={{ ...right, fontWeight: 700, color: k.count > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{k.count === 0 ? "None" : num(k.count)}</td>
              <td style={{ ...td, fontSize: 12, color: "var(--hf-text-muted)" }}>{k.why}</td>
            </tr>))}
          </tbody>
        </table>
      </Block>
      {er && (
        <>
          <StatRow>
            <Stat label="Cases opened" value={num(er.opened)} />
            <Stat label="Cases closed" value={num(er.closed)} hint={er.averageDaysToClose === null ? undefined : `${er.averageDaysToClose.toFixed(1)} days on average`} />
            <Stat label="Open now" value={num(er.open)} hint={er.longOpen > 0 ? `${er.longOpen} open more than 90 days` : undefined} />
            <Stat label="Hearings overdue" value={num(er.hearingsOverdue)} />
            <Stat label="Warnings expiring in 30 days" value={num(er.warningsExpiringSoon)} />
          </StatRow>
          <Breakdown title="Cases opened, by type" groups={er.byType} empty="No cases were opened." />
          <Breakdown title="Cases opened, by category" groups={er.byCategory} empty="No cases were opened." />
          <Breakdown title="Outcomes decided" groups={er.outcomes} empty="No outcomes were decided." />
          {er.confidentialLeftOut > 0 && <Notice kind="ok">{er.confidentialLeftOut} confidential case{er.confidentialLeftOut === 1 ? " is" : "s are"} not included in these figures.</Notice>}
        </>
      )}
      <Notes notes={c.notes} />
    </div>
  )
}
