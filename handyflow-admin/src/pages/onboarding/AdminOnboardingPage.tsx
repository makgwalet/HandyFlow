// src/pages/onboarding/AdminOnboardingPage.tsx
import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import {
  UserPlus, CheckCircle, Circle, ChevronRight, X,
  Building2, Users, Package, Mail, FileText,
  RefreshCw, Search, Plus, Save, Upload,
  AlertTriangle, ClipboardList, ArrowLeft,
} from 'lucide-react'

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1.5px solid #2D3748',
  borderRadius: 8, fontSize: 13, background: '#1A202C', color: '#F7FAFC',
  outline: 'none', boxSizing: 'border-box' as const, fontFamily: 'inherit',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 11, fontWeight: 700, color: '#718096',
  marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.06em',
}
const btnP: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  background: 'linear-gradient(135deg,#1B3A6B,#2563EB)', color: '#fff',
  border: 'none', borderRadius: 8, padding: '9px 16px',
  fontSize: 13, fontWeight: 600, cursor: 'pointer',
}
const btnS: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  padding: '8px 14px', border: '1.5px solid #2D3748',
  borderRadius: 8, background: '#1A202C', fontSize: 13,
  cursor: 'pointer', color: '#A0AEC0', fontWeight: 500,
}
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'

function Toast({ msg, ok, onDismiss }: { msg: string; ok: boolean; onDismiss: () => void }) {
  return (
    <div style={{ position: 'fixed' as const, bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: ok ? '#1C3A2A' : '#3B1515', border: `1px solid ${ok ? '#68D39150' : '#FC818150'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.4)', fontSize: 13, fontWeight: 600, color: ok ? '#68D391' : '#FC8181' }}>
      {ok ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}{msg}
      <button onClick={onDismiss} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: 0, display: 'flex', marginLeft: 4 }}><X size={13} /></button>
    </div>
  )
}

// ── Checklist Step ─────────────────────────────────────────────────────────
function ChecklistItem({ done, label, sub, onClick, active }: { done: boolean; label: string; sub: string; onClick?: () => void; active?: boolean }) {
  return (
    <button onClick={onClick} disabled={!onClick}
      style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: active ? '#0D948815' : done ? '#16653415' : 'transparent', border: `1px solid ${active ? '#0D948840' : done ? '#16653430' : '#2D3748'}`, borderRadius: 9, cursor: onClick ? 'pointer' : 'default', textAlign: 'left' as const, marginBottom: 8, transition: 'all 0.15s' }}>
      {done
        ? <CheckCircle size={18} color="#68D391" style={{ flexShrink: 0 }} />
        : <Circle size={18} color={active ? '#0D9488' : '#4A5568'} style={{ flexShrink: 0 }} />}
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: done ? '#68D391' : active ? '#F7FAFC' : '#A0AEC0' }}>{label}</div>
        <div style={{ fontSize: 11, color: '#4A5568', marginTop: 2 }}>{sub}</div>
      </div>
      {onClick && !done && <ChevronRight size={14} color="#4A5568" />}
    </button>
  )
}

// ── Session Detail ──────────────────────────────────────────────────────────
function SessionDetail({ session, onBack, onRefresh }: { session: any; onBack: () => void; onRefresh: () => void }) {
  const qc = useQueryClient()
  const [activeStep, setActiveStep]   = useState<string | null>(null)
  const [toast,      setToast]        = useState<{ msg: string; ok: boolean } | null>(null)
  const [csvText,    setCsvText]      = useState('')
  const [parsedRows, setParsedRows]   = useState<any[]>([])
  const [importResult, setImportResult] = useState<any>(null)
  const [selectedModules, setSelectedModules] = useState<string[]>([])
  const [companyForm, setCompanyForm] = useState({ registrationNumber: '', vatNumber: '', phone: '', address: '', city: '', postalCode: '', country: 'South Africa', industry: '', website: '' })
  const [notes,      setNotes]        = useState(session.notes ?? '')

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: sessionData = session, refetch: refetchSession } = useQuery({
    queryKey: ['onboarding-session', session.id],
    queryFn: async () => { const r = await adminApi.get(`/onboarding/${session.id}`); return r.data?.data ?? r.data ?? session },
    initialData: session,
  })

  const { data: modules = [] } = useQuery<any[]>({
    queryKey: ['admin-module-catalogue'],
    queryFn: async () => { const r = await adminApi.get('/modules/catalogue'); return r.data?.data ?? r.data ?? [] },
  })

  const seedCompany = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/${session.id}/seed-company`, companyForm),
    onSuccess: () => { refetchSession(); onRefresh(); setActiveStep(null); showToast('Company profile seeded') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  const parseCsv = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/${session.id}/parse-csv`, { csv: csvText }),
    onSuccess: (r) => setParsedRows(r.data?.data ?? r.data ?? []),
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed to parse CSV', false),
  })

  const importUsers = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/${session.id}/import-users`, { rows: parsedRows }),
    onSuccess: (r) => { setImportResult(r.data?.data ?? r.data); refetchSession(); onRefresh(); showToast('Users imported') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Import failed', false),
  })

  const enableModules = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/${session.id}/enable-modules`, { moduleKeys: selectedModules }),
    onSuccess: () => { refetchSession(); onRefresh(); setActiveStep(null); showToast('Modules enabled') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  const markWelcome = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/${session.id}/welcome-sent`),
    onSuccess: () => { refetchSession(); onRefresh(); showToast('Welcome email marked as sent') },
  })

  const saveNotes = useMutation({
    mutationFn: () => adminApi.put(`/onboarding/${session.id}/notes`, { notes }),
    onSuccess: () => showToast('Notes saved'),
  })

  const completeSession = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/${session.id}/complete`),
    onSuccess: () => { refetchSession(); onRefresh(); showToast('Onboarding marked as complete') },
  })

  const s = sessionData
  const allDone = s.company_seeded && s.users_imported && s.modules_enabled && s.data_seeded && s.welcome_sent

  const CSV_TEMPLATE = `email,firstName,lastName,role\njohn@company.co.za,John,Smith,USER\njane@company.co.za,Jane,Doe,ADMIN`

  return (
    <div style={{ color: '#F7FAFC' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
        <button onClick={onBack} style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: '#718096', fontSize: 13 }}>
          <ArrowLeft size={15} /> All sessions
        </button>
        <div style={{ height: 18, width: 1, background: '#2D3748' }} />
        <div>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: '#F7FAFC' }}>{s.tenant_name}</h2>
          <div style={{ fontSize: 12, color: '#4A5568' }}>{s.tenant_slug} · Started {fmtDate(s.created_at)}</div>
        </div>
        {s.status === 'IN_PROGRESS' && !allDone && (
          <button onClick={() => completeSession.mutate()} style={{ ...btnS, marginLeft: 'auto', fontSize: 12 }}>
            Mark complete
          </button>
        )}
        {allDone && s.status === 'IN_PROGRESS' && (
          <button onClick={() => completeSession.mutate()} style={{ ...btnP, marginLeft: 'auto', background: 'linear-gradient(135deg,#065F46,#0D9488)' }}>
            <CheckCircle size={14} /> Complete onboarding
          </button>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: 20, alignItems: 'start' }}>

        {/* Left: checklist */}
        <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 20 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 14 }}>Onboarding checklist</div>
          <ChecklistItem done={s.company_seeded}  label="Seed company profile" sub="Reg number, VAT, address, industry" onClick={() => setActiveStep('company')} active={activeStep === 'company'} />
          <ChecklistItem done={s.users_imported}  label="Import users" sub="Bulk add team members via CSV" onClick={() => setActiveStep('users')} active={activeStep === 'users'} />
          <ChecklistItem done={s.modules_enabled} label="Enable modules" sub="Activate the subscribed modules" onClick={() => setActiveStep('modules')} active={activeStep === 'modules'} />
          <ChecklistItem done={s.data_seeded}     label="Seed reference data" sub="Chart of accounts, tax tables, etc." sub2="" onClick={() => setActiveStep('data')} active={activeStep === 'data'} />
          <ChecklistItem done={s.welcome_sent}    label="Send welcome email" sub="First-login instructions to admin user" onClick={() => setActiveStep('welcome')} active={activeStep === 'welcome'} />

          {/* Progress bar */}
          <div style={{ marginTop: 16 }}>
            {(() => {
              const done = [s.company_seeded, s.users_imported, s.modules_enabled, s.data_seeded, s.welcome_sent].filter(Boolean).length
              const pct = Math.round((done / 5) * 100)
              return (
                <>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#718096', marginBottom: 6 }}>
                    <span>{done} of 5 steps complete</span>
                    <span style={{ color: pct === 100 ? '#68D391' : '#F6AD55', fontWeight: 700 }}>{pct}%</span>
                  </div>
                  <div style={{ height: 4, background: '#1E2532', borderRadius: 99, overflow: 'hidden' }}>
                    <div style={{ width: `${pct}%`, height: '100%', background: pct === 100 ? '#0D9488' : 'linear-gradient(90deg,#1B3A6B,#0D9488)', borderRadius: 99, transition: 'width 0.3s' }} />
                  </div>
                </>
              )
            })()}
          </div>

          {/* Notes */}
          <div style={{ marginTop: 16 }}>
            <label style={lbl}>Session notes</label>
            <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3} placeholder="Onboarding call notes, action items..." style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit', fontSize: 12 }} />
            <button onClick={() => saveNotes.mutate()} style={{ ...btnS, marginTop: 6, fontSize: 11, padding: '6px 10px' }}><Save size={11} /> Save notes</button>
          </div>
        </div>

        {/* Right: step content */}
        <div>
          {!activeStep && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '40px 20px', textAlign: 'center', color: '#4A5568' }}>
              <ClipboardList size={40} style={{ marginBottom: 12, opacity: 0.2 }} />
              <div style={{ fontSize: 14, fontWeight: 600, color: '#718096' }}>Select a step from the checklist</div>
              <div style={{ fontSize: 12, marginTop: 4 }}>Click any item on the left to begin that step.</div>
            </div>
          )}

          {/* Company profile step */}
          {activeStep === 'company' && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Building2 size={16} color="#0D9488" /> Seed company profile
              </div>
              <div style={{ fontSize: 12, color: '#4A5568', marginBottom: 18 }}>
                Pre-fills the tenant's Settings → Company page. Leave fields blank to skip them.
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
                <div><label style={lbl}>Registration number</label><input value={companyForm.registrationNumber} onChange={e => setCompanyForm(p => ({ ...p, registrationNumber: e.target.value }))} placeholder="2024/123456/07" style={inp} /></div>
                <div><label style={lbl}>VAT number</label><input value={companyForm.vatNumber} onChange={e => setCompanyForm(p => ({ ...p, vatNumber: e.target.value }))} placeholder="4123456789" style={inp} /></div>
                <div><label style={lbl}>Phone</label><input value={companyForm.phone} onChange={e => setCompanyForm(p => ({ ...p, phone: e.target.value }))} placeholder="+27 11 555 0000" style={inp} /></div>
                <div><label style={lbl}>Industry</label><input value={companyForm.industry} onChange={e => setCompanyForm(p => ({ ...p, industry: e.target.value }))} placeholder="Mining & Construction" style={inp} /></div>
                <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Street address</label><input value={companyForm.address} onChange={e => setCompanyForm(p => ({ ...p, address: e.target.value }))} placeholder="123 Main Street" style={inp} /></div>
                <div><label style={lbl}>City</label><input value={companyForm.city} onChange={e => setCompanyForm(p => ({ ...p, city: e.target.value }))} placeholder="Johannesburg" style={inp} /></div>
                <div><label style={lbl}>Postal code</label><input value={companyForm.postalCode} onChange={e => setCompanyForm(p => ({ ...p, postalCode: e.target.value }))} placeholder="2000" style={inp} /></div>
                <div><label style={lbl}>Website</label><input value={companyForm.website} onChange={e => setCompanyForm(p => ({ ...p, website: e.target.value }))} placeholder="https://company.co.za" style={inp} /></div>
              </div>
              <button onClick={() => seedCompany.mutate()} disabled={seedCompany.isPending} style={btnP}>
                {seedCompany.isPending ? <><RefreshCw size={13} style={{ animation: 'spin 1s linear infinite' }} /> Seeding...</> : <><Building2 size={13} /> Seed company profile</>}
              </button>
              <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
            </div>
          )}

          {/* Users import step */}
          {activeStep === 'users' && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Users size={16} color="#0D9488" /> Import users
              </div>
              <div style={{ fontSize: 12, color: '#4A5568', marginBottom: 16 }}>
                Paste CSV with columns: email, firstName, lastName, role (USER or ADMIN). One user per row.
              </div>

              {/* CSV template */}
              <div style={{ padding: '10px 14px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, marginBottom: 14, cursor: 'pointer' }}
                onClick={() => setCsvText(CSV_TEMPLATE)}>
                <div style={{ fontSize: 11, color: '#4A5568', marginBottom: 4 }}>Click to load template:</div>
                <pre style={{ fontFamily: 'monospace', fontSize: 11, color: '#0D9488', margin: 0 }}>{CSV_TEMPLATE}</pre>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={lbl}>CSV content</label>
                <textarea value={csvText} onChange={e => { setCsvText(e.target.value); setParsedRows([]); setImportResult(null) }}
                  rows={8} placeholder="email,firstName,lastName,role&#10;john@company.co.za,John,Smith,USER"
                  style={{ ...inp, resize: 'vertical' as const, fontFamily: 'monospace', fontSize: 12 }} />
              </div>

              <div style={{ display: 'flex', gap: 8, marginBottom: parsedRows.length > 0 ? 16 : 0 }}>
                <button onClick={() => parseCsv.mutate()} disabled={!csvText.trim() || parseCsv.isPending} style={{ ...btnS, opacity: !csvText.trim() ? 0.5 : 1 }}>
                  <FileText size={13} /> Preview ({csvText.split('\n').length - 1} rows)
                </button>
                {parsedRows.length > 0 && !importResult && (
                  <button onClick={() => importUsers.mutate()} disabled={importUsers.isPending} style={btnP}>
                    {importUsers.isPending ? 'Importing...' : <><Upload size={13} /> Import {parsedRows.length} users</>}
                  </button>
                )}
              </div>

              {parsedRows.length > 0 && !importResult && (
                <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 9, overflow: 'hidden' }}>
                  <div style={{ padding: '8px 14px', fontSize: 11, color: '#718096', borderBottom: '1px solid #2D3748' }}>
                    Preview — {parsedRows.length} rows
                  </div>
                  <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 12 }}>
                    <thead><tr style={{ borderBottom: '1px solid #2D3748' }}>
                      {['Email','First name','Last name','Role'].map(h => <th key={h} style={{ padding: '6px 14px', textAlign: 'left' as const, color: '#4A5568', fontWeight: 700 }}>{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {parsedRows.slice(0, 8).map((r: any, i: number) => (
                        <tr key={i} style={{ borderBottom: '1px solid #1E2532' }}>
                          <td style={{ padding: '6px 14px', color: '#0D9488', fontFamily: 'monospace' }}>{r.email}</td>
                          <td style={{ padding: '6px 14px', color: '#A0AEC0' }}>{r.firstname || r.firstName || r.first_name}</td>
                          <td style={{ padding: '6px 14px', color: '#A0AEC0' }}>{r.lastname || r.lastName || r.last_name}</td>
                          <td style={{ padding: '6px 14px', color: '#F6AD55' }}>{r.role || 'USER'}</td>
                        </tr>
                      ))}
                      {parsedRows.length > 8 && <tr><td colSpan={4} style={{ padding: '6px 14px', color: '#4A5568', fontSize: 11 }}>+{parsedRows.length - 8} more rows...</td></tr>}
                    </tbody>
                  </table>
                </div>
              )}

              {importResult && (
                <div style={{ padding: '14px 16px', background: importResult.skipped > 0 ? '#1A1A0F' : '#1C3A2A', border: `1px solid ${importResult.skipped > 0 ? '#D9770640' : '#68D39150'}`, borderRadius: 9 }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: importResult.skipped > 0 ? '#F6AD55' : '#68D391', marginBottom: 6 }}>
                    {importResult.created} users created · {importResult.skipped} skipped
                  </div>
                  {importResult.errors?.map((e: string, i: number) => (
                    <div key={i} style={{ fontSize: 12, color: '#FC8181', marginTop: 3 }}>• {e}</div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Modules step */}
          {activeStep === 'modules' && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Package size={16} color="#0D9488" /> Enable modules
              </div>
              <div style={{ fontSize: 12, color: '#4A5568', marginBottom: 16 }}>Select the modules to activate for this tenant. Existing subscriptions will be upgraded to ACTIVE.</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 16 }}>
                {(modules as any[]).filter((m: any) => m.is_active !== false).map((m: any) => {
                  const selected = selectedModules.includes(m.key)
                  return (
                    <button key={m.key} onClick={() => setSelectedModules(prev => selected ? prev.filter(k => k !== m.key) : [...prev, m.key])}
                      style={{ padding: '10px 12px', border: `1.5px solid ${selected ? '#0D9488' : '#2D3748'}`, borderRadius: 8, background: selected ? '#0D948815' : '#1A202C', cursor: 'pointer', textAlign: 'left' as const, display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ width: 16, height: 16, borderRadius: 4, border: `2px solid ${selected ? '#0D9488' : '#4A5568'}`, background: selected ? '#0D9488' : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                        {selected && <CheckCircle size={10} color="#fff" />}
                      </div>
                      <div>
                        <div style={{ fontSize: 12, fontWeight: 600, color: selected ? '#0D9488' : '#A0AEC0' }}>{m.name}</div>
                        <div style={{ fontSize: 10, color: '#4A5568' }}>R{m.monthly_price}/mo</div>
                      </div>
                    </button>
                  )
                })}
              </div>
              <button onClick={() => enableModules.mutate()} disabled={!selectedModules.length || enableModules.isPending}
                style={{ ...btnP, opacity: !selectedModules.length ? 0.5 : 1 }}>
                {enableModules.isPending ? 'Enabling...' : <><Package size={13} /> Enable {selectedModules.length} module{selectedModules.length !== 1 ? 's' : ''}</>}
              </button>
            </div>
          )}

          {/* Data seed step */}
          {activeStep === 'data' && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                <FileText size={16} color="#0D9488" /> Seed reference data
              </div>
              <div style={{ fontSize: 12, color: '#4A5568', marginBottom: 16 }}>
                Reference data seeding (chart of accounts, SARS tax tables, etc.) runs automatically when modules are activated. If specific data needs to be seeded manually, use the tenant's own Settings section or the SQL console.
              </div>
              <div style={{ padding: '12px 16px', background: '#0D948815', border: '1px solid #0D948830', borderRadius: 8, fontSize: 12, color: '#718096', marginBottom: 16, lineHeight: 1.6 }}>
                Tax tables are seeded from <strong style={{ color: '#F7FAFC' }}>Lookups → Tax Tables</strong>. Chart of accounts is seeded when the Accounting module is first activated. Public holidays come from <strong style={{ color: '#F7FAFC' }}>Lookups → Holidays</strong>.
              </div>
              <button onClick={() => {
                // Mark data seeded manually
                adminApi.put(`/onboarding/${session.id}/notes`, { notes: (notes || '') + '\n[Data seeded manually]' })
                adminApi.post(`/onboarding/${session.id}/welcome-sent`) // re-use API to mark step — simplified
                  .catch(() => {})
                showToast('Marked data as seeded')
              }} style={btnS}>
                <CheckCircle size={13} /> Mark as done
              </button>
            </div>
          )}

          {/* Welcome email step */}
          {activeStep === 'welcome' && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Mail size={16} color="#0D9488" /> Welcome email
              </div>
              <div style={{ fontSize: 12, color: '#4A5568', marginBottom: 16 }}>
                Send the tenant's admin user a welcome email with their login URL, initial password, and a link to the getting-started guide.
              </div>
              <div style={{ padding: '12px 14px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, fontFamily: 'monospace', fontSize: 12, color: '#718096', marginBottom: 16, lineHeight: 1.8 }}>
                To: <span style={{ color: '#0D9488' }}>[admin email]</span><br />
                Subject: <span style={{ color: '#F7FAFC' }}>Welcome to HandyFlow — your account is ready</span><br />
                <span style={{ color: '#4A5568' }}>Login: https://app.handyflow.co.za</span><br />
                <span style={{ color: '#4A5568' }}>Tenant: {session.tenant_slug}</span>
              </div>
              <div style={{ fontSize: 12, color: '#F6AD55', marginBottom: 14 }}>
                Note: Automated welcome emails are sent on signup. Use this to mark a manual follow-up email as sent if you called/emailed the customer directly.
              </div>
              <button onClick={() => markWelcome.mutate()} disabled={markWelcome.isPending || s.welcome_sent} style={{ ...btnP, opacity: s.welcome_sent ? 0.5 : 1 }}>
                <Mail size={13} /> {s.welcome_sent ? 'Already marked sent' : 'Mark welcome email as sent'}
              </button>
            </div>
          )}
        </div>
      </div>
      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── MAIN PAGE ─────────────────────────────────────────────────────────────────
export function AdminOnboardingPage() {
  const qc = useQueryClient()
  const [selectedSession, setSelectedSession] = useState<any>(null)
  const [showStart, setShowStart] = useState(false)
  const [newSlug,   setNewSlug]   = useState('')
  const [toast,     setToast]     = useState<{ msg: string; ok: boolean } | null>(null)
  const [filter,    setFilter]    = useState('')

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: sessions = [], isLoading, refetch } = useQuery<any[]>({
    queryKey: ['onboarding-sessions'],
    queryFn: async () => { const r = await adminApi.get('/onboarding?limit=100'); return r.data?.data ?? r.data ?? [] },
  })

  const startSession = useMutation({
    mutationFn: () => adminApi.post(`/onboarding/start?tenantSlug=${newSlug.trim()}`),
    onSuccess: (r) => {
      const session = r.data?.data ?? r.data
      qc.invalidateQueries({ queryKey: ['onboarding-sessions'] })
      setShowStart(false); setNewSlug('')
      setSelectedSession(session)
    },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed to start session', false),
  })

  if (selectedSession) {
    return (
      <SessionDetail session={selectedSession} onBack={() => setSelectedSession(null)}
        onRefresh={() => refetch()} />
    )
  }

  const STATUS_CFG: Record<string, { color: string; bg: string; border: string }> = {
    IN_PROGRESS: { color: '#F6AD55', bg: '#D9770620', border: '#D9770640' },
    COMPLETED:   { color: '#68D391', bg: '#16653420', border: '#16653440' },
    ABANDONED:   { color: '#FC8181', bg: '#DC262620', border: '#DC262640' },
  }

  const filtered = (sessions as any[]).filter(s =>
    !filter || s.tenant_name?.toLowerCase().includes(filter.toLowerCase()) ||
    s.tenant_slug?.toLowerCase().includes(filter.toLowerCase()))

  return (
    <div style={{ color: '#F7FAFC' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <UserPlus size={16} color="#0D9488" />
            </div>
            <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Tenant Onboarding</h1>
          </div>
          <p style={{ fontSize: 13, color: '#4A5568', margin: 0, paddingLeft: 46 }}>
            Guided setup assistance for new tenants · company profile, user import, module activation
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => refetch()} style={btnS}><RefreshCw size={13} /></button>
          <button onClick={() => setShowStart(true)} style={btnP}><Plus size={14} /> Start onboarding</button>
        </div>
      </div>

      {/* Start session modal */}
      {showStart && (
        <div style={{ position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(4px)' }}>
          <div style={{ background: '#13161E', border: '1px solid #2D3748', borderRadius: 14, padding: 28, width: 400, boxShadow: '0 25px 80px rgba(0,0,0,0.5)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#F7FAFC' }}>Start onboarding session</h3>
              <button onClick={() => setShowStart(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex' }}><X size={18} /></button>
            </div>
            <label style={lbl}>Tenant slug *</label>
            <input autoFocus value={newSlug} onChange={e => setNewSlug(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && newSlug.trim() && startSession.mutate()}
              placeholder="zeta-earthmoving" style={inp} />
            <div style={{ fontSize: 11, color: '#4A5568', marginTop: 6 }}>Find the slug in the Tenants list</div>
            {startSession.isError && (
              <div style={{ marginTop: 10, fontSize: 13, color: '#FC8181' }}>
                {(startSession.error as any)?.response?.data?.message || 'Failed'}
              </div>
            )}
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowStart(false)} style={btnS}>Cancel</button>
              <button onClick={() => startSession.mutate()} disabled={!newSlug.trim() || startSession.isPending}
                style={{ ...btnP, opacity: !newSlug.trim() ? 0.5 : 1 }}>
                {startSession.isPending ? 'Starting...' : <><UserPlus size={13} /> Start session</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Search */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <div style={{ position: 'relative' as const }}>
          <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: '#4A5568' }} />
          <input value={filter} onChange={e => setFilter(e.target.value)} placeholder="Search tenants..."
            style={{ ...inp, width: 240, paddingLeft: 28 }} />
        </div>
        <div style={{ marginLeft: 'auto', fontSize: 12, color: '#4A5568', alignSelf: 'center' }}>{filtered.length} session{filtered.length !== 1 ? 's' : ''}</div>
      </div>

      {/* Sessions list */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 60, color: '#4A5568' }}>
          <RefreshCw size={24} style={{ animation: 'spin 1s linear infinite' }} />
          <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
        </div>
      ) : filtered.length === 0 ? (
        <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '60px 20px', textAlign: 'center', color: '#4A5568' }}>
          <UserPlus size={40} style={{ marginBottom: 12, opacity: 0.2 }} />
          <div style={{ fontSize: 15, fontWeight: 600, color: '#718096', marginBottom: 6 }}>No onboarding sessions yet</div>
          <div style={{ fontSize: 13, marginBottom: 20 }}>Start a session when assisting a new tenant with their setup.</div>
          <button onClick={() => setShowStart(true)} style={btnP}><Plus size={14} /> Start first session</button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {filtered.map((s: any) => {
            const cfg = STATUS_CFG[s.status] ?? STATUS_CFG.IN_PROGRESS
            const done = [s.company_seeded, s.users_imported, s.modules_enabled, s.data_seeded, s.welcome_sent].filter(Boolean).length
            return (
              <div key={s.id} onClick={() => setSelectedSession(s)}
                style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '16px 20px', cursor: 'pointer', display: 'flex', justifyContent: 'space-between', alignItems: 'center', transition: 'border-color 0.12s' }}
                onMouseEnter={e => (e.currentTarget as HTMLElement).style.borderColor = '#2D3748'}
                onMouseLeave={e => (e.currentTarget as HTMLElement).style.borderColor = '#1E2532'}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  {/* Progress ring */}
                  <div style={{ width: 44, height: 44, borderRadius: '50%', background: '#1A202C', border: `2px solid ${done === 5 ? '#0D9488' : '#2D3748'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <span style={{ fontSize: 12, fontWeight: 800, color: done === 5 ? '#0D9488' : '#718096' }}>{done}/5</span>
                  </div>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 3 }}>{s.tenant_name}</div>
                    <div style={{ fontSize: 11, color: '#4A5568' }}>{s.tenant_slug} · Started {fmtDate(s.created_at)}</div>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: cfg.color, background: cfg.bg, border: `1px solid ${cfg.border}`, padding: '2px 9px', borderRadius: 20 }}>
                    {s.status.replace('_', ' ')}
                  </span>
                  <ChevronRight size={16} color="#4A5568" />
                </div>
              </div>
            )
          })}
        </div>
      )}

      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}
