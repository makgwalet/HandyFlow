import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Search, User, ChevronRight, AlertTriangle } from "lucide-react"

interface Employee {
  id: string
  employeeNumber: string
  firstName: string
  lastName: string
  fullName: string
  idNumber: string
  taxNumber: string
  email: string
  phone: string
  employmentType: string
  jobTitle: string
  department: string
  startDate: string
  endDate: string | null
  status: string
  grossSalary: number
  payFrequency: string
}

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  ACTIVE:     { color: "#166534", bg: "#DCFCE7" },
  TERMINATED: { color: "#DC2626", bg: "#FEF2F2" },
  ON_LEAVE:   { color: "#D97706", bg: "#FFFBEB" },
  PROBATION:  { color: "#7C3AED", bg: "#F5F3FF" },
}

const SA_BANKS = ["First National Bank", "Standard Bank", "ABSA", "Nedbank", "Capitec Bank", "African Bank", "Investec", "TymeBank"]
const EMP_TYPES = ["PERMANENT", "CONTRACT", "PART_TIME", "CASUAL"]
const PAY_FREQ  = ["MONTHLY", "WEEKLY", "FORTNIGHTLY"]

export default function EmployeesTab() {
  const qc = useQueryClient()
  const [search, setSearch]       = useState("")
  const [statusFilter, setStatus] = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<Employee | null>(null)
  const [showTerminate, setShowTerminate] = useState(false)
  const [terminateDate, setTerminateDate] = useState(new Date().toISOString().split("T")[0])
  const [error, setError] = useState("")

  const initForm = () => ({
    firstName: "", lastName: "", idNumber: "", taxNumber: "",
    dateOfBirth: "", gender: "", race: "", email: "", phone: "",
    startDate: new Date().toISOString().split("T")[0],
    employmentType: "PERMANENT", jobTitle: "", department: "",
    grossSalary: "", payFrequency: "MONTHLY",
    bankName: "", bankAccountNumber: "", bankBranchCode: "",
    medicalAidContribution: "", pensionContribution: "", travelAllowance: "",
    emergencyContactName: "", emergencyContactPhone: "", notes: "",
  })
  const [form, setForm] = useState(initForm())

  const { data: page, isLoading } = useQuery({
    queryKey: ["hr-employees", statusFilter, search],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (statusFilter) params.set("status", statusFilter)
      if (search)       params.set("search", search)
      const r = await apiClient.get(`/api/v1/hr/employees?${params}`)
      return r.data
    },
  })

  const createEmployee = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/hr/employees", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employees"] }); setShowCreate(false); setForm(initForm()) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create employee"),
  })

  const terminateEmployee = useMutation({
    mutationFn: ({ id, date }: { id: string; date: string }) =>
      apiClient.post(`/api/v1/hr/employees/${id}/terminate?endDate=${date}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employees"] }); setShowTerminate(false); setSelected(null) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to terminate employee"),
  })

  const employees: Employee[] = page?.content || []
  const fmtR = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
  const f = (key: keyof typeof form, val: string) => setForm(p => ({ ...p, [key]: val }))

  const handleCreate = () => {
    if (!form.firstName || !form.lastName || !form.grossSalary || !form.startDate) {
      setError("First name, last name, start date and gross salary are required"); return
    }
    createEmployee.mutate({
      ...form,
      grossSalary: parseFloat(form.grossSalary),
      medicalAidContribution: parseFloat(form.medicalAidContribution) || null,
      pensionContribution:    parseFloat(form.pensionContribution)    || null,
      travelAllowance:        parseFloat(form.travelAllowance)        || null,
      dateOfBirth: form.dateOfBirth || null,
    })
  }

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          {["", "ACTIVE", "PROBATION", "ON_LEAVE", "TERMINATED"].map(s => (
            <button key={s} onClick={() => setStatus(s)} style={filterBtn(statusFilter === s)}>
              {s || "All"}
            </button>
          ))}
          <div style={{ position: "relative" }}>
            <Search size={14} style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search employees..."
              style={{ padding: "6px 10px 6px 30px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 13 }} />
          </div>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> Add Employee</button>
      </div>

      {/* Stats */}
      <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap" }}>
        {["ACTIVE", "PROBATION", "ON_LEAVE"].map(s => {
          const style = STATUS_STYLE[s] || { color: "#475569", bg: "#F8FAFC" }
          const count = employees.filter(e => e.status === s).length
          return (
            <div key={s} style={{ background: style.bg, borderRadius: 8, padding: "10px 16px", minWidth: 90 }}>
              <div style={{ fontSize: 20, fontWeight: 700, color: style.color }}>{count}</div>
              <div style={{ fontSize: 11, color: style.color, opacity: 0.8 }}>{s.replace("_", " ")}</div>
            </div>
          )
        })}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading employees...</div>
      ) : employees.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <User size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No employees found</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC" }}>
                <th style={th}>Employee</th>
                <th style={th}>Job Title</th>
                <th style={th}>Department</th>
                <th style={th}>Type</th>
                <th style={th}>Gross Salary</th>
                <th style={th}>Status</th>
                <th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {employees.map((emp, i) => {
                const style = STATUS_STYLE[emp.status] || { color: "#475569", bg: "#F8FAFC" }
                return (
                  <tr key={emp.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", cursor: "pointer" }}
                    onClick={() => setSelected(emp)}>
                    <td style={td}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <div style={{ width: 34, height: 34, borderRadius: "50%", background: "#F0FDF4", border: "1px solid #86EFAC", display: "flex", alignItems: "center", justifyContent: "center" }}>
                          <span style={{ fontSize: 13, fontWeight: 700, color: "#0D9488" }}>{emp.firstName?.[0]}{emp.lastName?.[0]}</span>
                        </div>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{emp.fullName}</div>
                          <div style={{ fontSize: 11, color: "#94A3B8" }}>{emp.employeeNumber}</div>
                        </div>
                      </div>
                    </td>
                    <td style={td}>{emp.jobTitle || "—"}</td>
                    <td style={td}>{emp.department || "—"}</td>
                    <td style={td}><span style={{ fontSize: 12, color: "#64748B" }}>{emp.employmentType}</span></td>
                    <td style={td}><span style={{ fontWeight: 600 }}>{fmtR(emp.grossSalary)}</span></td>
                    <td style={td}>
                      <span style={{ background: style.bg, color: style.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{emp.status}</span>
                    </td>
                    <td style={td}><ChevronRight size={16} color="#94A3B8" /></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Employee detail modal */}
      {selected && !showTerminate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 48, height: 48, borderRadius: "50%", background: "#F0FDF4", border: "2px solid #86EFAC", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <span style={{ fontSize: 18, fontWeight: 700, color: "#0D9488" }}>{selected.firstName?.[0]}{selected.lastName?.[0]}</span>
                </div>
                <div>
                  <h3 style={{ margin: "0 0 2px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{selected.fullName}</h3>
                  <div style={{ fontSize: 12, color: "#64748B" }}>{selected.employeeNumber} · {selected.jobTitle || "No title"}</div>
                </div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 20 }}>
              {[
                ["Department", selected.department],
                ["Employment Type", selected.employmentType],
                ["Start Date", selected.startDate],
                ["Pay Frequency", selected.payFrequency],
                ["Gross Salary", fmtR(selected.grossSalary)],
                ["Email", selected.email],
                ["Phone", selected.phone],
                ["ID Number", selected.idNumber],
                ["Tax Number", selected.taxNumber],
              ].filter(([, v]) => v).map(([label, value]) => (
                <div key={label as string}>
                  <div style={{ fontSize: 11, fontWeight: 600, color: "#94A3B8", marginBottom: 2 }}>{(label as string).toUpperCase()}</div>
                  <div style={{ fontSize: 13, color: "#0F172A" }}>{value as string}</div>
                </div>
              ))}
            </div>

            {selected.status === "ACTIVE" || selected.status === "PROBATION" ? (
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <button onClick={() => setShowTerminate(true)}
                  style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, cursor: "pointer" }}>
                  <AlertTriangle size={14} /> Terminate Employee
                </button>
              </div>
            ) : null}
          </div>
        </div>
      )}

      {/* Terminate confirm */}
      {showTerminate && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700, color: "#DC2626" }}>Terminate Employee</h3>
            <p style={{ margin: "0 0 20px", fontSize: 13, color: "#64748B" }}>
              This will mark <strong>{selected.fullName}</strong> as terminated. This action cannot be undone.
            </p>
            <Field label="Termination Date">
              <input type="date" value={terminateDate} onChange={e => setTerminateDate(e.target.value)} style={inputStyle} />
            </Field>
            {error && <div style={{ marginTop: 8, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowTerminate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => terminateEmployee.mutate({ id: selected.id, date: terminateDate })}
                style={{ padding: "9px 18px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, cursor: "pointer" }}>
                Terminate
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create employee modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 680, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Register Employee</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <Section title="Personal Details">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <Field label="First Name *"><input value={form.firstName} onChange={e => f("firstName", e.target.value)} placeholder="James" style={inputStyle} /></Field>
                <Field label="Last Name *"><input value={form.lastName} onChange={e => f("lastName", e.target.value)} placeholder="Dlamini" style={inputStyle} /></Field>
                <Field label="ID Number"><input value={form.idNumber} onChange={e => f("idNumber", e.target.value)} placeholder="8501015026083" style={inputStyle} /></Field>
                <Field label="Tax Number"><input value={form.taxNumber} onChange={e => f("taxNumber", e.target.value)} placeholder="1234567890" style={inputStyle} /></Field>
                <Field label="Date of Birth"><input type="date" value={form.dateOfBirth} onChange={e => f("dateOfBirth", e.target.value)} style={inputStyle} /></Field>
                <Field label="Gender">
                  <select value={form.gender} onChange={e => f("gender", e.target.value)} style={inputStyle}>
                    <option value="">Select...</option>
                    {["MALE", "FEMALE", "NON_BINARY", "PREFER_NOT_TO_SAY"].map(g => <option key={g} value={g}>{g.replace("_", " ")}</option>)}
                  </select>
                </Field>
                <Field label="Email"><input value={form.email} onChange={e => f("email", e.target.value)} placeholder="james@company.co.za" style={inputStyle} /></Field>
                <Field label="Phone"><input value={form.phone} onChange={e => f("phone", e.target.value)} placeholder="+27 82 123 4567" style={inputStyle} /></Field>
              </div>
            </Section>

            <Section title="Employment">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <Field label="Start Date *"><input type="date" value={form.startDate} onChange={e => f("startDate", e.target.value)} style={inputStyle} /></Field>
                <Field label="Employment Type">
                  <select value={form.employmentType} onChange={e => f("employmentType", e.target.value)} style={inputStyle}>
                    {EMP_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </Field>
                <Field label="Job Title"><input value={form.jobTitle} onChange={e => f("jobTitle", e.target.value)} placeholder="Operator" style={inputStyle} /></Field>
                <Field label="Department"><input value={form.department} onChange={e => f("department", e.target.value)} placeholder="Operations" style={inputStyle} /></Field>
                <Field label="Gross Salary (R) *"><input type="number" value={form.grossSalary} onChange={e => f("grossSalary", e.target.value)} placeholder="25000" style={inputStyle} /></Field>
                <Field label="Pay Frequency">
                  <select value={form.payFrequency} onChange={e => f("payFrequency", e.target.value)} style={inputStyle}>
                    {PAY_FREQ.map(p => <option key={p} value={p}>{p}</option>)}
                  </select>
                </Field>
              </div>
            </Section>

            <Section title="Allowances & Deductions">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
                <Field label="Travel Allowance"><input type="number" value={form.travelAllowance} onChange={e => f("travelAllowance", e.target.value)} placeholder="0.00" style={inputStyle} /></Field>
                <Field label="Medical Aid"><input type="number" value={form.medicalAidContribution} onChange={e => f("medicalAidContribution", e.target.value)} placeholder="0.00" style={inputStyle} /></Field>
                <Field label="Pension"><input type="number" value={form.pensionContribution} onChange={e => f("pensionContribution", e.target.value)} placeholder="0.00" style={inputStyle} /></Field>
              </div>
            </Section>

            <Section title="Banking">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
                <Field label="Bank Name">
                  <select value={form.bankName} onChange={e => f("bankName", e.target.value)} style={inputStyle}>
                    <option value="">Select bank...</option>
                    {SA_BANKS.map(b => <option key={b} value={b}>{b}</option>)}
                  </select>
                </Field>
                <Field label="Account Number"><input value={form.bankAccountNumber} onChange={e => f("bankAccountNumber", e.target.value)} placeholder="62012345678" style={inputStyle} /></Field>
                <Field label="Branch Code"><input value={form.bankBranchCode} onChange={e => f("bankBranchCode", e.target.value)} placeholder="250655" style={inputStyle} /></Field>
              </div>
            </Section>

            {error && <div style={{ color: "#DC2626", fontSize: 13, marginBottom: 10 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 8 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={handleCreate} disabled={createEmployee.isPending} style={btnPrimary}>
                {createEmployee.isPending ? "Registering..." : "Register Employee"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.06em", marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>
      {children}
    </div>
  )
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}

const filterBtn = (active: boolean): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 6, fontSize: 12, cursor: "pointer",
  border: active ? "1px solid #0D9488" : "1px solid #E2E8F0",
  background: active ? "#F0FDF4" : "#fff",
  color: active ? "#0D9488" : "#64748B",
  fontWeight: active ? 600 : 400,
})
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }
const th: React.CSSProperties = { padding: "10px 16px", textAlign: "left", fontSize: 11, fontWeight: 600, color: "#64748B", letterSpacing: "0.05em", borderBottom: "1px solid #E2E8F0" }
const td: React.CSSProperties = { padding: "12px 16px", fontSize: 13, borderBottom: "1px solid #F1F5F9" }
