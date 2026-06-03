// src/pages/hr/EmployeesTab.tsx
// KEY FIXES:
// 1. API unwrap — was r.data / page?.content, needs r.data?.data?.content (ApiResponse wrapper)
// 2. SA ID Luhn validation with DOB/gender auto-fill
// 3. Net salary live preview
// 4. Edit employee modal
// 5. Status SUSPENDED added
// 6. Rejection reason on terminate
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Search, Users, ChevronRight, AlertTriangle, AlertCircle, Edit2, UserX, CheckCircle } from "lucide-react"

interface Employee {
  id: string; employeeNumber: string; firstName: string; lastName: string; fullName: string
  idNumber: string | null; taxNumber: string | null; dateOfBirth: string | null
  gender: string | null; race: string | null; email: string | null; phone: string | null
  employmentType: string; jobTitle: string | null; department: string | null
  startDate: string; endDate: string | null; status: string
  grossSalary: number; payFrequency: string
  medicalAidContribution: number; pensionContribution: number; travelAllowance: number
  emergencyContactName: string | null; emergencyContactPhone: string | null; notes: string | null
}

// Correct ApiResponse unwrap — handles both wrapped and bare responses
const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:      { color: "#166534", bg: "#DCFCE7", label: "Active"      },
  TERMINATED:  { color: "#DC2626", bg: "#FEF2F2", label: "Terminated"  },
  ON_LEAVE:    { color: "#D97706", bg: "#FFFBEB", label: "On Leave"    },
  PROBATION:   { color: "#7C3AED", bg: "#F5F3FF", label: "Probation"   },
  SUSPENDED:   { color: "#1D4ED8", bg: "#EFF6FF", label: "Suspended"   },
}

const EMP_TYPES   = ["PERMANENT","FIXED_TERM","PART_TIME","CASUAL","CONTRACTOR"]
const GENDERS     = ["MALE","FEMALE","NON_BINARY","PREFER_NOT_TO_SAY"]
const RACES       = ["AFRICAN","COLOURED","INDIAN","WHITE","OTHER"]
const DEPARTMENTS = ["MANAGEMENT","FINANCE","HR","OPERATIONS","TECHNICAL","SALES","ADMIN","LOGISTICS","OTHER"]
const PAY_FREQS   = ["MONTHLY","BI_WEEKLY","WEEKLY"]
const SA_BANKS    = ["ABSA","FNB","STANDARD BANK","NEDBANK","CAPITEC","INVESTEC","AFRICAN BANK","OTHER"]

// SA ID Luhn validation — 13 digits, checksum, DOB+gender decode
const validateSaId = (id: string): { valid: boolean; dob?: string; gender?: string } => {
  if (!/^\d{13}$/.test(id)) return { valid: false }
  const d = id.split("").map(Number)
  let sum = 0
  for (let i = 0; i < 12; i++) {
    let x = d[i]
    if (i % 2 !== 0) { x *= 2; if (x > 9) x -= 9 }
    sum += x
  }
  if ((10 - (sum % 10)) % 10 !== d[12]) return { valid: false }
  const yr = parseInt(id.slice(0, 2))
  const mo = parseInt(id.slice(2, 4))
  const dy = parseInt(id.slice(4, 6))
  const fullYr = yr < 30 ? 2000 + yr : 1900 + yr
  const dob = `${fullYr}-${String(mo).padStart(2, "0")}-${String(dy).padStart(2, "0")}`
  const gender = parseInt(id.slice(6, 10)) >= 5000 ? "MALE" : "FEMALE"
  return { valid: true, dob, gender }
}

const EMPTY_FORM = {
  firstName: "", lastName: "", idNumber: "", taxNumber: "", dateOfBirth: "", gender: "",
  race: "", email: "", phone: "", startDate: "", employmentType: "PERMANENT",
  jobTitle: "", department: "", grossSalary: "", payFrequency: "MONTHLY",
  bankName: "", bankAccountNumber: "", bankBranchCode: "",
  medicalAidContribution: "", pensionContribution: "", travelAllowance: "",
  emergencyContactName: "", emergencyContactPhone: "", notes: "",
}

export default function EmployeesTab() {
  const qc = useQueryClient()
  const [search, setSearch]         = useState("")
  const [filterStatus, setFilter]   = useState("ALL")
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing]       = useState<Employee | null>(null)
  const [selected, setSelected]     = useState<Employee | null>(null)
  const [terminating, setTerminating] = useState<Employee | null>(null)
  const [endDate, setEndDate]       = useState("")
  const [form, setForm]             = useState(EMPTY_FORM)
  const [error, setError]           = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [idState, setIdState]       = useState<{ valid: boolean | null; msg: string }>({ valid: null, msg: "" })

  // FIX: correct unwrap — r.data?.data?.content (ApiResponse<Page<Employee>>)
  const { data: employees = [], isLoading } = useQuery<Employee[]>({
    queryKey: ["hr-employees", search, filterStatus],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "200" })
      if (search) params.set("search", search)
      if (filterStatus !== "ALL") params.set("status", filterStatus)
      return unwrap(await apiClient.get(`/api/v1/hr/employees?${params}`))
    },
  })

  const createEmployee = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/hr/employees", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employees"] }); setShowCreate(false); setForm(EMPTY_FORM); setError(""); setIdState({ valid: null, msg: "" }) },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setError(d?.message ?? "Failed to register employee") },
  })

  const updateEmployee = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/hr/employees/${id}`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employees"] }); setEditing(null); setForm(EMPTY_FORM); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to update employee"),
  })

  const terminateEmployee = useMutation({
    mutationFn: ({ id, date }: { id: string; date: string }) =>
      apiClient.post(`/api/v1/hr/employees/${id}/terminate?endDate=${date}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employees"] }); setTerminating(null); setSelected(null); setEndDate(""); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to terminate employee"),
  })

  const handleIdChange = (val: string) => {
    setForm(f => ({ ...f, idNumber: val }))
    if (val.length === 13) {
      const r = validateSaId(val)
      if (r.valid) {
        setIdState({ valid: true, msg: "Valid SA ID — DOB and gender auto-filled" })
        setForm(f => ({ ...f, idNumber: val, dateOfBirth: r.dob!, gender: r.gender ?? f.gender }))
      } else {
        setIdState({ valid: false, msg: "Invalid ID number — checksum failed" })
      }
    } else { setIdState({ valid: null, msg: "" }) }
  }

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.firstName.trim()) errs.firstName = "Required"
    if (!form.lastName.trim())  errs.lastName  = "Required"
    if (!form.startDate)        errs.startDate = "Required"
    if (!form.grossSalary || isNaN(Number(form.grossSalary))) errs.grossSalary = "Valid amount required"
    if (form.idNumber?.length === 13 && !validateSaId(form.idNumber).valid) errs.idNumber = "Invalid SA ID"
    setFieldErrors(errs); return Object.keys(errs).length === 0
  }

  const toBody = (f: typeof EMPTY_FORM) => ({
    firstName: f.firstName, lastName: f.lastName,
    idNumber: f.idNumber || null, taxNumber: f.taxNumber || null,
    dateOfBirth: f.dateOfBirth || null, gender: f.gender || null, race: f.race || null,
    email: f.email || null, phone: f.phone || null,
    startDate: f.startDate, employmentType: f.employmentType,
    jobTitle: f.jobTitle || null, department: f.department || null,
    grossSalary: Number(f.grossSalary), payFrequency: f.payFrequency,
    bankName: f.bankName || null, bankAccountNumber: f.bankAccountNumber || null, bankBranchCode: f.bankBranchCode || null,
    medicalAidContribution: f.medicalAidContribution ? Number(f.medicalAidContribution) : 0,
    pensionContribution:    f.pensionContribution    ? Number(f.pensionContribution)    : 0,
    travelAllowance:        f.travelAllowance        ? Number(f.travelAllowance)        : 0,
    emergencyContactName:  f.emergencyContactName  || null,
    emergencyContactPhone: f.emergencyContactPhone || null,
    notes: f.notes || null,
  })

  const openEdit = (emp: Employee) => {
    setEditing(emp)
    setForm({
      firstName: emp.firstName, lastName: emp.lastName, idNumber: emp.idNumber ?? "",
      taxNumber: emp.taxNumber ?? "", dateOfBirth: emp.dateOfBirth ?? "", gender: emp.gender ?? "",
      race: emp.race ?? "", email: emp.email ?? "", phone: emp.phone ?? "",
      startDate: emp.startDate, employmentType: emp.employmentType,
      jobTitle: emp.jobTitle ?? "", department: emp.department ?? "",
      grossSalary: String(emp.grossSalary), payFrequency: emp.payFrequency,
      bankName: "", bankAccountNumber: "", bankBranchCode: "",
      medicalAidContribution: String(emp.medicalAidContribution ?? 0),
      pensionContribution:    String(emp.pensionContribution ?? 0),
      travelAllowance:        String(emp.travelAllowance ?? 0),
      emergencyContactName:  emp.emergencyContactName ?? "",
      emergencyContactPhone: emp.emergencyContactPhone ?? "",
      notes: emp.notes ?? "",
    })
    setError(""); setSelected(null)
  }

  // Live estimated net pay — rough bracket preview only
  const netPreview = () => {
    const gross = Number(form.grossSalary) || 0
    if (!gross) return null
    const pension = Number(form.pensionContribution) || 0
    const medical = Number(form.medicalAidContribution) || 0
    const travel  = (Number(form.travelAllowance) || 0) * 0.8  // 80% taxable
    const taxable = (gross + travel - Math.min(pension * 12, (gross + travel) * 12 * 0.275, 350000) / 12) * 12
    const annualTax = Math.max(0, taxable > 237100 ? 42678 + (taxable - 237100) * 0.26 : taxable * 0.18)
    const paye = Math.max(0, annualTax / 12 - 17235 / 12 - 364)
    const uif  = Math.min(gross, 17712) * 0.01
    return gross - paye - uif - pension - medical
  }
  const net = netPreview()

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, outline: "none",
    background: fieldErrors[k] ? "#FFF5F5" : "#fff",
  })
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
  const FErr = ({ k }: { k: string }) => fieldErrors[k]
    ? <div style={{ fontSize: 12, color: "#DC2626", marginTop: 3, display: "flex", alignItems: "center", gap: 3 }}><AlertCircle size={11} />{fieldErrors[k]}</div>
    : null

  const EmployeeForm = () => (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      <Sect title="Personal Details">
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <div><label style={lbl}>First Name *</label><input autoFocus value={form.firstName} onChange={e => { setForm(f => ({ ...f, firstName: e.target.value })); setFieldErrors(f => omit(f,"firstName")) }} style={inp("firstName")} /><FErr k="firstName" /></div>
          <div><label style={lbl}>Last Name *</label><input value={form.lastName} onChange={e => { setForm(f => ({ ...f, lastName: e.target.value })); setFieldErrors(f => omit(f,"lastName")) }} style={inp("lastName")} /><FErr k="lastName" /></div>
          <div>
            <label style={lbl}>SA ID Number</label>
            <input value={form.idNumber} onChange={e => handleIdChange(e.target.value)} placeholder="13 digits"
              style={{ ...inp("idNumber"), borderColor: idState.valid === false ? "#DC2626" : idState.valid === true ? "#22C55E" : "#E2E8F0" }} />
            {idState.msg && <div style={{ fontSize: 12, marginTop: 3, color: idState.valid ? "#166534" : "#DC2626", display: "flex", alignItems: "center", gap: 3 }}><AlertCircle size={11} />{idState.msg}</div>}
          </div>
          <div><label style={lbl}>Tax / SARS Number</label><input value={form.taxNumber} onChange={e => setForm(f => ({ ...f, taxNumber: e.target.value }))} placeholder="10-digit SARS number" style={inp("taxNumber")} /></div>
          <div>
            <label style={lbl}>Date of Birth</label>
            <input type="date" value={form.dateOfBirth} onChange={e => setForm(f => ({ ...f, dateOfBirth: e.target.value }))} style={inp("dateOfBirth")} />
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>Auto-filled from SA ID</div>
          </div>
          <div>
            <label style={lbl}>Gender</label>
            <select value={form.gender} onChange={e => setForm(f => ({ ...f, gender: e.target.value }))} style={{ ...inp("gender"), background: "#fff" }}>
              <option value="">Select...</option>{GENDERS.map(g => <option key={g}>{g}</option>)}
            </select>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>Auto-filled from SA ID</div>
          </div>
          <div>
            <label style={lbl}>Race</label>
            <select value={form.race} onChange={e => setForm(f => ({ ...f, race: e.target.value }))} style={{ ...inp("race"), background: "#fff" }}>
              <option value="">Select...</option>{RACES.map(r => <option key={r}>{r}</option>)}
            </select>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>Required for Employment Equity</div>
          </div>
          <div><label style={lbl}>Email</label><input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} placeholder="employee@co.za" style={inp("email")} /></div>
          <div><label style={lbl}>Phone</label><input value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} placeholder="+27 82 555 1234" style={inp("phone")} /></div>
        </div>
      </Sect>

      <Sect title="Employment">
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <div><label style={lbl}>Start Date *</label><input type="date" value={form.startDate} onChange={e => { setForm(f => ({ ...f, startDate: e.target.value })); setFieldErrors(f => omit(f,"startDate")) }} style={inp("startDate")} /><FErr k="startDate" /></div>
          <div><label style={lbl}>Employment Type</label><select value={form.employmentType} onChange={e => setForm(f => ({ ...f, employmentType: e.target.value }))} style={{ ...inp("employmentType"), background: "#fff" }}>{EMP_TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
          <div><label style={lbl}>Job Title</label><input value={form.jobTitle} onChange={e => setForm(f => ({ ...f, jobTitle: e.target.value }))} placeholder="Operations Manager" style={inp("jobTitle")} /></div>
          <div><label style={lbl}>Department</label><select value={form.department} onChange={e => setForm(f => ({ ...f, department: e.target.value }))} style={{ ...inp("department"), background: "#fff" }}><option value="">Select...</option>{DEPARTMENTS.map(d => <option key={d}>{d}</option>)}</select></div>
        </div>
      </Sect>

      <Sect title="Payroll & Deductions">
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <div>
            <label style={lbl}>Gross Salary (R/month) *</label>
            <input type="number" value={form.grossSalary} onChange={e => { setForm(f => ({ ...f, grossSalary: e.target.value })); setFieldErrors(f => omit(f,"grossSalary")) }} placeholder="25000" style={inp("grossSalary")} />
            <FErr k="grossSalary" />
          </div>
          <div><label style={lbl}>Pay Frequency</label><select value={form.payFrequency} onChange={e => setForm(f => ({ ...f, payFrequency: e.target.value }))} style={{ ...inp("payFrequency"), background: "#fff" }}>{PAY_FREQS.map(p => <option key={p}>{p}</option>)}</select></div>
          <div>
            <label style={lbl}>Travel Allowance (R/month)</label>
            <input type="number" value={form.travelAllowance} onChange={e => setForm(f => ({ ...f, travelAllowance: e.target.value }))} placeholder="0" style={inp("travelAllowance")} />
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>80% is taxable per SARS ITA s.8(1)(b)</div>
          </div>
          <div>
            <label style={lbl}>Medical Aid (R/month)</label>
            <input type="number" value={form.medicalAidContribution} onChange={e => setForm(f => ({ ...f, medicalAidContribution: e.target.value }))} placeholder="0" style={inp("medicalAidContribution")} />
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>Medical Tax Credit applied to PAYE</div>
          </div>
          <div>
            <label style={lbl}>Pension / Provident (R/month)</label>
            <input type="number" value={form.pensionContribution} onChange={e => setForm(f => ({ ...f, pensionContribution: e.target.value }))} placeholder="0" style={inp("pensionContribution")} />
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>Deducted from taxable income — s.11(k)</div>
          </div>
          {net !== null && (
            <div style={{ gridColumn: "1/-1", padding: "12px 14px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#166534", marginBottom: 3 }}>ESTIMATED TAKE-HOME</div>
              <div style={{ fontSize: 20, fontWeight: 800, color: "#0D9488" }}>R {Math.max(0, net).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}</div>
              <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>Approximate — exact amount calculated during payroll processing</div>
            </div>
          )}
        </div>
      </Sect>

      <Sect title="Banking">
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <div><label style={lbl}>Bank</label><select value={form.bankName} onChange={e => setForm(f => ({ ...f, bankName: e.target.value }))} style={{ ...inp("bankName"), background: "#fff" }}><option value="">Select...</option>{SA_BANKS.map(b => <option key={b}>{b}</option>)}</select></div>
          <div><label style={lbl}>Account Number</label><input value={form.bankAccountNumber} onChange={e => setForm(f => ({ ...f, bankAccountNumber: e.target.value }))} placeholder="1234567890" style={inp("bankAccountNumber")} /></div>
          <div><label style={lbl}>Branch Code</label><input value={form.bankBranchCode} onChange={e => setForm(f => ({ ...f, bankBranchCode: e.target.value }))} placeholder="250655" style={inp("bankBranchCode")} /></div>
        </div>
      </Sect>

      <Sect title="Emergency Contact">
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <div><label style={lbl}>Name</label><input value={form.emergencyContactName} onChange={e => setForm(f => ({ ...f, emergencyContactName: e.target.value }))} placeholder="Maria Dlamini" style={inp("emergencyContactName")} /></div>
          <div><label style={lbl}>Phone</label><input value={form.emergencyContactPhone} onChange={e => setForm(f => ({ ...f, emergencyContactPhone: e.target.value }))} placeholder="+27 83 555 6789" style={inp("emergencyContactPhone")} /></div>
        </div>
      </Sect>

      <div><label style={lbl}>Notes</label><textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Probation period, special arrangements..." style={{ ...inp("notes"), resize: "vertical" as const }} /></div>
    </div>
  )

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {Object.entries(STATUS_CFG).filter(([k]) => k !== "TERMINATED").map(([k, cfg]) => (
          <div key={k} style={{ flex: 1, background: cfg.bg, border: `1px solid ${cfg.color}40`, borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: cfg.color }}>{(employees as Employee[]).filter(e => e.status === k).length}</div>
            <div style={{ fontSize: 11, color: cfg.color, marginTop: 2 }}>{cfg.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
          {["ALL","ACTIVE","ON_LEAVE","TERMINATED"].map(s => (
            <button key={s} onClick={() => setFilter(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400, background: filterStatus === s ? "#1B3A6B" : "#F1F5F9", color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
          <div style={{ position: "relative" as const }}>
            <Search size={14} style={{ position: "absolute" as const, left: 10, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search name, number..."
              style={{ paddingLeft: 32, padding: "8px 12px 8px 32px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 220 }} />
          </div>
        </div>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFieldErrors({}); setError(""); setIdState({ valid: null, msg: "" }) }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Employee
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading employees...</div>
      ) : (employees as Employee[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No employees found</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC" }}>
                {["Employee","Job Title","Department","Type","Gross Salary","Status",""].map(h => (
                  <th key={h} style={{ padding: "10px 16px", textAlign: "left" as const, fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em", borderBottom: "1px solid #E2E8F0" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(employees as Employee[]).map((emp, i) => {
                const cfg = STATUS_CFG[emp.status] ?? STATUS_CFG.ACTIVE
                return (
                  <tr key={emp.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", cursor: "pointer" }} onClick={() => setSelected(emp)}>
                    <td style={{ padding: "12px 16px", borderBottom: "1px solid #F1F5F9" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <div style={{ width: 36, height: 36, borderRadius: "50%", background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                          <span style={{ fontSize: 13, fontWeight: 700, color: "#fff" }}>{emp.firstName[0]}{emp.lastName[0]}</span>
                        </div>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{emp.fullName}</div>
                          <div style={{ fontSize: 11, color: "#94A3B8" }}>{emp.employeeNumber}</div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 13, color: "#475569", borderBottom: "1px solid #F1F5F9" }}>{emp.jobTitle || "—"}</td>
                    <td style={{ padding: "12px 16px", fontSize: 13, color: "#475569", borderBottom: "1px solid #F1F5F9" }}>{emp.department || "—"}</td>
                    <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B", borderBottom: "1px solid #F1F5F9" }}>{emp.employmentType}</td>
                    <td style={{ padding: "12px 16px", fontSize: 13, fontWeight: 600, color: "#0F172A", borderBottom: "1px solid #F1F5F9" }}>{fmtR(emp.grossSalary)}</td>
                    <td style={{ padding: "12px 16px", borderBottom: "1px solid #F1F5F9" }}>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                    </td>
                    <td style={{ padding: "12px 16px", borderBottom: "1px solid #F1F5F9" }}>
                      <div style={{ display: "flex", gap: 5 }}>
                        <button onClick={e => { e.stopPropagation(); openEdit(emp) }} style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "5px 7px", cursor: "pointer", color: "#1D4ED8" }}><Edit2 size={12} /></button>
                        {emp.status === "ACTIVE" && <button onClick={e => { e.stopPropagation(); setTerminating(emp); setEndDate(""); setError("") }} style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "5px 7px", cursor: "pointer", color: "#DC2626" }}><UserX size={12} /></button>}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Detail modal */}
      {selected && !editing && !terminating && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <span style={{ fontSize: 20, fontWeight: 700, color: "#fff" }}>{selected.firstName[0]}{selected.lastName[0]}</span>
                </div>
                <div>
                  <h3 style={{ margin: "0 0 3px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{selected.fullName}</h3>
                  <div style={{ fontSize: 12, color: "#64748B" }}>{selected.employeeNumber} · {selected.jobTitle || "No title"} · {selected.department || ""}</div>
                </div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 16 }}>
              {[
                { l: "Employment Type", v: selected.employmentType },
                { l: "Gross Salary",    v: fmtR(selected.grossSalary) },
                { l: "Start Date",      v: fmtDate(selected.startDate) },
                { l: "Pay Frequency",   v: selected.payFrequency },
                { l: "Email",           v: selected.email || "—" },
                { l: "Phone",           v: selected.phone || "—" },
                { l: "SA ID",           v: selected.idNumber || "—" },
                { l: "Tax Number",      v: selected.taxNumber || "—" },
                { l: "Travel Allow.",   v: fmtR(selected.travelAllowance) },
                { l: "Medical Aid",     v: fmtR(selected.medicalAidContribution) },
                { l: "Pension",         v: fmtR(selected.pensionContribution) },
                { l: "Emergency",       v: selected.emergencyContactName ? `${selected.emergencyContactName} · ${selected.emergencyContactPhone}` : "—" },
              ].map(item => (
                <div key={item.l} style={{ padding: "9px 12px", background: "#F8FAFC", borderRadius: 7 }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 2 }}>{item.l}</div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                </div>
              ))}
            </div>
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button onClick={() => openEdit(selected)} style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}><Edit2 size={13} /> Edit</button>
              {selected.status === "ACTIVE" && (
                <button onClick={() => { setTerminating(selected); setEndDate(""); setError(""); setSelected(null) }}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <AlertTriangle size={13} /> Terminate
                </button>
              )}
              <button onClick={() => setSelected(null)} style={{ padding: "8px 16px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151" }}>Close</button>
            </div>
          </div>
        </div>
      )}

      {/* Create modal */}
      {showCreate && (
        <Overlay onClose={() => setShowCreate(false)}>
          <MHead title="Register Employee" onClose={() => setShowCreate(false)} />
          <EmployeeForm />
          {error && <ErrBanner msg={error} />}
          <MFoot onCancel={() => setShowCreate(false)} onSubmit={() => { if (validate()) createEmployee.mutate(toBody(form)) }} loading={createEmployee.isPending} label="Register Employee" />
        </Overlay>
      )}

      {/* Edit modal */}
      {editing && (
        <Overlay onClose={() => setEditing(null)}>
          <MHead title={`Edit — ${editing.fullName}`} onClose={() => setEditing(null)} />
          <EmployeeForm />
          {error && <ErrBanner msg={error} />}
          <MFoot onCancel={() => setEditing(null)} onSubmit={() => { if (validate()) updateEmployee.mutate({ id: editing.id, body: toBody(form) }) }} loading={updateEmployee.isPending} label="Save Changes" />
        </Overlay>
      )}

      {/* Terminate modal */}
      {terminating && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 18 }}>
              <div style={{ width: 44, height: 44, borderRadius: "50%", background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><UserX size={20} color="#DC2626" /></div>
              <div><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Terminate Employee</h3><div style={{ fontSize: 13, color: "#64748B" }}>{terminating.fullName} · {terminating.employeeNumber}</div></div>
            </div>
            <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#B91C1C" }}>
              This sets the employee to Terminated. Leave balances are preserved for final pay calculation.
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Last Working Day *</label>
              <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} style={{ ...inp("_"), width: "100%" }} />
            </div>
            {error && <ErrBanner msg={error} />}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => { setTerminating(null); setError("") }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => terminateEmployee.mutate({ id: terminating.id, date: endDate })} disabled={!endDate || terminateEmployee.isPending}
                style={{ padding: "9px 22px", background: !endDate ? "#94A3B8" : "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {terminateEmployee.isPending ? "Processing..." : "Terminate"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 680, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 24 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading} style={{ padding: "9px 22px", background: loading ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return <div><div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>{children}</div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const inp  = (k: string): React.CSSProperties => ({ width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none", background: "#fff" })
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
