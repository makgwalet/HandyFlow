import { useState, useRef, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Building2, Upload, Save, CreditCard, MapPin, Phone, X, Check, Users, Receipt } from "lucide-react"
import TeamTab from "./TeamTab"

interface TenantProfile {
  id: string
  companyName: string
  slug: string
  vatNumber: string | null
  phone: string | null
  email: string
  address: Record<string, string> | null
  logoUrl: string | null
  bankName: string | null
  bankAccount: string | null
  bankBranch: string | null
  paymentTerms: string | null
  // NEW (identity module modernization): the backend has always written
  // these on PUT .../me/billing-contact, but TenantDetails never returned
  // them and this form had nothing to show — see the new "Billing Contact"
  // section below and TenantDetails.java's own comment for the backend
  // half of this fix.
  billingEmail: string | null
  billingContactName: string | null
  billingPhone: string | null
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

type Tab = "company" | "team"

export function SettingsPage() {
  const qc = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [activeTab, setActiveTab] = useState<Tab>("company")
  const [saved, setSaved] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [logoPreview, setLogoPreview] = useState<string | null>(null)

  const { data: profile, isLoading } = useQuery<TenantProfile>({
    queryKey: ["tenant-profile"],
    staleTime: 0,
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/identity/tenants/me")
      return res.data
    },
  })

  const [form, setForm] = useState({
    name: "", phone: "", vatNumber: "",
    street: "", suburb: "", city: "", province: "", postalCode: "",
    bankName: "", bankAccount: "", bankBranch: "", paymentTerms: "",
  })
  // NEW (identity module modernization): backs the new "Billing Contact"
  // section below — see TenantProfile's own comment for why this data
  // wasn't previously reachable from the frontend at all.
  const [billingForm, setBillingForm] = useState({
    billingEmail: "", billingContactName: "", billingPhone: "",
  })
  // NEW: inline field-level errors for the Company form — this form
  // previously had zero validation of any kind before calling Save
  // (confirmed: handleSave sent whatever was in `form` unconditionally).
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (profile) {
      setError("")
      setForm({
        name:         profile.companyName || "",
        phone:        profile.phone || "",
        vatNumber:    profile.vatNumber || "",
        street:       profile.address?.street || "",
        suburb:       profile.address?.suburb || "",
        city:         profile.address?.city || "",
        province:     profile.address?.province || "",
        postalCode:   profile.address?.postalCode || "",
        bankName:     profile.bankName || "",
        bankAccount:  profile.bankAccount || "",
        bankBranch:   profile.bankBranch || "",
        paymentTerms: profile.paymentTerms || "",
      })
      setBillingForm({
        billingEmail:       profile.billingEmail || "",
        billingContactName: profile.billingContactName || "",
        billingPhone:       profile.billingPhone || "",
      })
    }
  }, [profile])

  const updateProfile = useMutation({
    mutationFn: (body: any) => apiClient.put("/api/v1/identity/tenants/me", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tenant-profile"] })
      setSaved("profile")
      setTimeout(() => setSaved(null), 3000)
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to save profile"),
  })

  // NEW (identity module modernization): wired to the now-round-tripping
  // PUT /api/v1/identity/tenants/me/billing-contact endpoint.
  const updateBillingContact = useMutation({
    mutationFn: (body: any) => apiClient.put("/api/v1/identity/tenants/me/billing-contact", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tenant-profile"] })
      setSaved("billing")
      setTimeout(() => setSaved(null), 3000)
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to save billing contact"),
  })

  const uploadLogo = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/identity/tenants/me/logo", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tenant-profile"] })
      setSaved("logo")
      setTimeout(() => setSaved(null), 3000)
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to upload logo"),
  })

  const handleLogoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 200 * 1024) { setError("Logo must be under 200KB"); return }
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      setLogoPreview(result)
      const base64 = result.split(",")[1]
      uploadLogo.mutate({ logoBase64: base64, mimeType: file.type })
    }
    reader.readAsDataURL(file)
  }

  const handleSave = () => {
    // FIX (identity module modernization): this form previously had zero
    // validation of any kind — handleSave sent whatever was in `form`
    // unconditionally, so an admin could clear "Company Name" entirely
    // and Save would succeed, backed by a PUT with no server-side name
    // validation either (Tenant.updateProfile()'s own blank-guard on
    // name was the only thing standing between that and a truly empty
    // company name — everything else on this form had no guard at all,
    // client or server). Mirrors the length caps just added to
    // UpdateTenantProfileRequest.java so the person sees the same rule
    // here, before a round-trip, rather than only from a 400.
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Company name is required"
    else if (form.name.trim().length > 255) errs.name = "Company name must be 255 characters or fewer"
    if (form.vatNumber && form.vatNumber.length > 30) errs.vatNumber = "VAT number must be 30 characters or fewer"
    if (form.phone && form.phone.length > 30) errs.phone = "Phone number must be 30 characters or fewer"
    if (form.bankAccount && form.bankAccount.length > 50) errs.bankAccount = "Account number must be 50 characters or fewer"
    if (form.bankBranch && form.bankBranch.length > 20) errs.bankBranch = "Branch code must be 20 characters or fewer"
    if (form.paymentTerms && form.paymentTerms.length > 1000) errs.paymentTerms = "Payment terms must be 1000 characters or fewer"
    setFieldErrors(errs)
    if (Object.keys(errs).length > 0) {
      setError("Please fix the highlighted fields before saving")
      return
    }
    setError("")
    updateProfile.mutate({
      name:         form.name.trim() || null,
      phone:        form.phone || null,
      vatNumber:    form.vatNumber || null,
      address: {
        street:     form.street,
        suburb:     form.suburb,
        city:       form.city,
        province:   form.province,
        postalCode: form.postalCode,
      },
      bankName:     form.bankName || null,
      bankAccount:  form.bankAccount || null,
      bankBranch:   form.bankBranch || null,
      paymentTerms: form.paymentTerms || null,
    })
  }

  // NEW (identity module modernization): validates the new Billing
  // Contact form before submit — email format (the field is @Email on
  // the backend, so a malformed address would 400 anyway; catching it
  // here gives an inline reason instead) and the same length caps as
  // UpdateBillingContactRequest's sibling DTOs.
  const handleSaveBilling = () => {
    const errs: Record<string, string> = {}
    if (billingForm.billingEmail && !EMAIL_RE.test(billingForm.billingEmail)) {
      errs.billingEmail = "Enter a valid email address"
    }
    if (billingForm.billingContactName && billingForm.billingContactName.length > 150) {
      errs.billingContactName = "Name must be 150 characters or fewer"
    }
    if (billingForm.billingPhone && billingForm.billingPhone.length > 30) {
      errs.billingPhone = "Phone number must be 30 characters or fewer"
    }
    setFieldErrors(errs)
    if (Object.keys(errs).length > 0) {
      setError("Please fix the highlighted fields before saving")
      return
    }
    setError("")
    updateBillingContact.mutate({
      billingEmail:       billingForm.billingEmail.trim() || null,
      billingContactName: billingForm.billingContactName.trim() || null,
      billingPhone:       billingForm.billingPhone.trim() || null,
    })
  }

  const currentLogo = logoPreview || profile?.logoUrl

  if (isLoading) return (
    <div style={{ textAlign: "center", padding: 60, color: "#94A3B8" }}>Loading settings...</div>
  )

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>Settings</h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>Manage your company profile and team</p>
      </div>

      {/* Tab bar */}
      <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 28 }}>
        {[
          { id: "company" as Tab, label: "Company",      icon: Building2 },
          { id: "team"    as Tab, label: "Team & Roles", icon: Users     },
        ].map(({ id, label, icon: Icon }) => (
          <button key={id} onClick={() => setActiveTab(id)}
            style={{
              display: "flex", alignItems: "center", gap: 7,
              padding: "10px 18px", background: "none", border: "none",
              borderBottom: activeTab === id ? "2px solid #0D9488" : "2px solid transparent",
              color: activeTab === id ? "#0D9488" : "#64748B",
              fontWeight: activeTab === id ? 700 : 400,
              fontSize: 14, cursor: "pointer", marginBottom: -1,
            }}>
            <Icon size={15} />{label}
          </button>
        ))}
      </div>

      {/* ── Company tab ───────────────────────────────────────────── */}
      {activeTab === "company" && (
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <Section title="Company Identity" icon={Building2}>
            <div style={{ display: "flex", gap: 24, alignItems: "flex-start" }}>
              <div style={{ flexShrink: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 8 }}>Company Logo</div>
                <div
                  onClick={() => fileRef.current?.click()}
                  style={{ width: 120, height: 120, borderRadius: 12, border: "2px dashed #CBD5E1", background: "#F8FAFC", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", cursor: "pointer", overflow: "hidden", transition: "border-color 0.15s" }}
                  onMouseEnter={e => (e.currentTarget.style.borderColor = "#0D9488")}
                  onMouseLeave={e => (e.currentTarget.style.borderColor = "#CBD5E1")}
                >
                  {currentLogo ? (
                    <img src={currentLogo} alt="Company logo" style={{ width: "100%", height: "100%", objectFit: "contain", padding: 8 }} />
                  ) : (
                    <><Upload size={24} color="#94A3B8" style={{ marginBottom: 6 }} /><span style={{ fontSize: 11, color: "#94A3B8", textAlign: "center", padding: "0 8px" }}>Click to upload</span></>
                  )}
                </div>
                <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/svg+xml" style={{ display: "none" }} onChange={handleLogoChange} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 6, textAlign: "center" }}>PNG, JPG or SVG · max 200KB</div>
                {saved === "logo" && <div style={{ fontSize: 11, color: "#0D9488", marginTop: 4, textAlign: "center", display: "flex", alignItems: "center", gap: 4, justifyContent: "center" }}><Check size={12} /> Logo saved</div>}
                {currentLogo && <button onClick={() => { setLogoPreview(null); uploadLogo.mutate({ logoBase64: "", mimeType: "image/png" }) }} style={{ ...btnOutline, width: "100%", marginTop: 6, fontSize: 11, padding: "4px 8px" }}><X size={11} /> Remove</button>}
              </div>
              <div style={{ flex: 1, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div style={{ gridColumn: "1 / -1" }}>
                  <Field label="Company Name *" error={fieldErrors.name}>
                    <Input value={form.name} onChange={v => { setForm(f => ({ ...f, name: v })); setFieldErrors(e => ({ ...e, name: "" })) }} placeholder="Zeta Earthmoving (Pty) Ltd" error={!!fieldErrors.name} />
                  </Field>
                </div>
                <Field label="VAT Registration Number" error={fieldErrors.vatNumber}>
                  <Input value={form.vatNumber} onChange={v => { setForm(f => ({ ...f, vatNumber: v })); setFieldErrors(e => ({ ...e, vatNumber: "" })) }} placeholder="4560123456" error={!!fieldErrors.vatNumber} />
                </Field>
                <Field label="Phone Number" error={fieldErrors.phone}>
                  <Input value={form.phone} onChange={v => { setForm(f => ({ ...f, phone: v })); setFieldErrors(e => ({ ...e, phone: "" })) }} placeholder="+27 11 555 0100" error={!!fieldErrors.phone} />
                </Field>
                <div style={{ gridColumn: "1 / -1", fontSize: 12, color: "#94A3B8", padding: "8px 12px", background: "#F8FAFC", borderRadius: 8 }}>
                  Email: <strong style={{ color: "#475569" }}>{profile?.email}</strong> · Slug: <strong style={{ color: "#475569" }}>{profile?.slug}</strong>
                  <span style={{ marginLeft: 8, color: "#94A3B8" }}>(contact support to change these)</span>
                </div>
              </div>
            </div>
          </Section>

          <Section title="Business Address" icon={MapPin}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Street Address"><Input value={form.street} onChange={v => setForm(f => ({ ...f, street: v }))} placeholder="12 Mine Road" /></Field>
              </div>
              <Field label="Suburb"><Input value={form.suburb} onChange={v => setForm(f => ({ ...f, suburb: v }))} placeholder="Carletonville" /></Field>
              <Field label="City"><Input value={form.city} onChange={v => setForm(f => ({ ...f, city: v }))} placeholder="Merafong" /></Field>
              <Field label="Province">
                <select value={form.province} onChange={e => setForm(f => ({ ...f, province: e.target.value }))} style={selectStyle}>
                  <option value="">Select province...</option>
                  {["Gauteng","Western Cape","KwaZulu-Natal","Eastern Cape","Limpopo","Mpumalanga","North West","Free State","Northern Cape"].map(p => <option key={p} value={p}>{p}</option>)}
                </select>
              </Field>
              <Field label="Postal Code"><Input value={form.postalCode} onChange={v => setForm(f => ({ ...f, postalCode: v }))} placeholder="2499" /></Field>
            </div>
          </Section>

          <Section title="Banking Details" icon={CreditCard}>
            <div style={{ marginBottom: 12, fontSize: 13, color: "#64748B", padding: "10px 14px", background: "#F0F9FF", borderRadius: 8, borderLeft: "3px solid #0D9488" }}>
              These details appear on all invoices and payment requests sent to clients.
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <Field label="Bank Name">
                <select value={form.bankName} onChange={e => setForm(f => ({ ...f, bankName: e.target.value }))} style={selectStyle}>
                  <option value="">Select bank...</option>
                  {["First National Bank","Standard Bank","ABSA","Nedbank","Capitec Bank","African Bank","Investec","Bidvest Bank","TymeBank"].map(b => <option key={b} value={b}>{b}</option>)}
                </select>
              </Field>
              <Field label="Account Number" error={fieldErrors.bankAccount}>
                <Input value={form.bankAccount} onChange={v => { setForm(f => ({ ...f, bankAccount: v })); setFieldErrors(e => ({ ...e, bankAccount: "" })) }} placeholder="62012345678" error={!!fieldErrors.bankAccount} />
              </Field>
              <Field label="Branch Code" error={fieldErrors.bankBranch}>
                <Input value={form.bankBranch} onChange={v => { setForm(f => ({ ...f, bankBranch: v })); setFieldErrors(e => ({ ...e, bankBranch: "" })) }} placeholder="250655" error={!!fieldErrors.bankBranch} />
              </Field>
            </div>
          </Section>

          <Section title="Invoice Payment Terms" icon={Phone}>
            <Field label="Default payment terms (printed on every invoice)" error={fieldErrors.paymentTerms}>
              <textarea value={form.paymentTerms} onChange={e => { setForm(f => ({ ...f, paymentTerms: e.target.value })); setFieldErrors(er => ({ ...er, paymentTerms: "" })) }} placeholder="Payment due within 30 days of invoice date. EFT payments only." rows={3}
                style={{ width: "100%", padding: "9px 12px", border: `1px solid ${fieldErrors.paymentTerms ? "#FCA5A5" : "#E2E8F0"}`, borderRadius: 8, fontSize: 14, resize: "vertical", boxSizing: "border-box", fontFamily: "inherit", color: "#0F172A" }} />
            </Field>
          </Section>

          {error && <div style={{ padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{error}</div>}

          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
            {saved === "profile" && <div style={{ display: "flex", alignItems: "center", gap: 6, color: "#0D9488", fontSize: 14, fontWeight: 500 }}><Check size={16} /> Changes saved</div>}
            <button onClick={handleSave} disabled={updateProfile.isPending} style={btnPrimary}>
              <Save size={15} />{updateProfile.isPending ? "Saving..." : "Save Changes"}
            </button>
          </div>

          {/* NEW (identity module modernization): the backend has always
              supported this via PUT .../me/billing-contact — see
              TenantDetails.java's comment for why the frontend had no
              way to show or edit it until now. Kept as its own Section
              with its own Save button (not merged into the Company form
              above) since it's a genuinely separate concept — who gets
              billing/subscription email, not the company's own
              identity/address/banking details — and saves independently
              via its own mutation, matching how Logo already saves
              separately from the rest of the Company form. */}
          <Section title="Billing Contact" icon={Receipt}>
            <div style={{ marginBottom: 12, fontSize: 13, color: "#64748B", padding: "10px 14px", background: "#F0F9FF", borderRadius: 8, borderLeft: "3px solid #0D9488" }}>
              Subscription invoices, payment receipts, and billing notices go here instead of every team member's inbox. Leave blank to use the company email above.
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Billing Contact Name" error={fieldErrors.billingContactName}>
                  <Input value={billingForm.billingContactName} onChange={v => { setBillingForm(f => ({ ...f, billingContactName: v })); setFieldErrors(e => ({ ...e, billingContactName: "" })) }} placeholder="Thabo Nkosi" error={!!fieldErrors.billingContactName} />
                </Field>
              </div>
              <Field label="Billing Email" error={fieldErrors.billingEmail}>
                <Input type="email" value={billingForm.billingEmail} onChange={v => { setBillingForm(f => ({ ...f, billingEmail: v })); setFieldErrors(e => ({ ...e, billingEmail: "" })) }} placeholder="accounts@company.co.za" error={!!fieldErrors.billingEmail} />
              </Field>
              <Field label="Billing Phone" error={fieldErrors.billingPhone}>
                <Input value={billingForm.billingPhone} onChange={v => { setBillingForm(f => ({ ...f, billingPhone: v })); setFieldErrors(e => ({ ...e, billingPhone: "" })) }} placeholder="+27 11 555 0100" error={!!fieldErrors.billingPhone} />
              </Field>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 16 }}>
              {saved === "billing" && <div style={{ display: "flex", alignItems: "center", gap: 6, color: "#0D9488", fontSize: 14, fontWeight: 500 }}><Check size={16} /> Billing contact saved</div>}
              <button onClick={handleSaveBilling} disabled={updateBillingContact.isPending} style={btnPrimary}>
                <Save size={15} />{updateBillingContact.isPending ? "Saving..." : "Save Billing Contact"}
              </button>
            </div>
          </Section>
        </div>
      )}

      {/* ── Team tab ──────────────────────────────────────────────── */}
      {activeTab === "team" && <TeamTab />}
    </div>
  )
}

// ── Shared components ──────────────────────────────────────────────────────

function Section({ title, icon: Icon, children }: { title: string; icon: React.ElementType; children: React.ReactNode }) {
  return (
    <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 24 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 20, paddingBottom: 16, borderBottom: "1px solid #F1F5F9" }}>
        <div style={{ width: 32, height: 32, borderRadius: 8, background: "#F0FDF4", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Icon size={16} color="#0D9488" />
        </div>
        <h2 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: "#0F172A" }}>{title}</h2>
      </div>
      {children}
    </div>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>
      {children}
      {error && <div style={{ marginTop: 4, fontSize: 12, color: "#DC2626" }}>{error}</div>}
    </div>
  )
}

function Input({ value, onChange, placeholder, type = "text", error }: { value: string; onChange: (v: string) => void; placeholder?: string; type?: string; error?: boolean }) {
  return (
    <input type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
      style={{ width: "100%", padding: "9px 12px", border: `1px solid ${error ? "#FCA5A5" : "#E2E8F0"}`, borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, color: "#0F172A" }} />
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "10px 20px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnOutline: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", gap: 5, background: "#fff", color: "#64748B", border: "1px solid #E2E8F0", borderRadius: 6, padding: "6px 12px", fontSize: 13, cursor: "pointer" }
const selectStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", color: "#0F172A" }
