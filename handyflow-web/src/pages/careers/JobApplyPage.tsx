import { useState, useRef } from 'react'
import type { CSSProperties } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { MapPin, Briefcase, Clock, GraduationCap, ArrowLeft, Upload, X, CheckCircle2 } from 'lucide-react'

// See CareersListPage.tsx for the full rationale — baseURL must match
// apiClient's (src/api/client.ts) or relative paths resolve against the
// Vite dev server instead of the backend.
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

interface JobDetail {
  id: string; title: string; department: string | null; location: string | null
  jobType: string; experienceLevel: string
  description: string; requirements: string | null; benefits: string | null
  salaryMin: number | null; salaryMax: number | null; showSalary: boolean
  salaryCurrency?: string; companyName: string | null
}
interface ApplyResult {
  applicationId: string; jobTitle: string; companyName: string
  applicantName: string; stageLabel: string
}

const JOB_TYPE_LABEL: Record<string, string> = {
  FULL_TIME: 'Full-time', PART_TIME: 'Part-time', CONTRACT: 'Contract',
  INTERNSHIP: 'Internship', FREELANCE: 'Freelance',
}
const EXPERIENCE_LABEL: Record<string, string> = {
  JUNIOR: 'Junior', MID: 'Mid-level', SENIOR: 'Senior', LEAD: 'Lead', EXECUTIVE: 'Executive',
}

const paper = '#FAF8F4'
const ink = '#1C1A16'
const muted = '#736C5E'
const line = '#E7E2D6'
const accent = '#0F5138'
const accentSoft = '#E6F0EA'
const danger = '#B3441E'
const dangerSoft = '#FBEEE7'

const fontImport = `@import url('https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600&family=Inter:wght@400;500;600&display=swap');`

const inputStyle: CSSProperties = {
  width: '100%', boxSizing: 'border-box', padding: '11px 14px', fontSize: 15,
  border: `1px solid ${line}`, borderRadius: 9, background: '#fff', color: ink,
  fontFamily: 'inherit',
}
const labelStyle: CSSProperties = {
  display: 'block', fontSize: 13, fontWeight: 500, color: ink, marginBottom: 6,
}

const MAX_CV_BYTES = 5 * 1024 * 1024 // 5MB — reasonable client-side guard; cv storage is
// base64-in-DB on the backend (a known, separately-flagged gap, not something to fix here),
// so keeping uploads modest matters more than usual.

export function JobApplyPage() {
  const { tenantSlug, jobSlug } = useParams<{ tenantSlug: string; jobSlug: string }>()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [location, setLocation] = useState('')
  const [linkedinUrl, setLinkedinUrl] = useState('')
  const [portfolioUrl, setPortfolioUrl] = useState('')
  const [cvFile, setCvFile] = useState<File | null>(null)
  const [cvError, setCvError] = useState('')
  const [formError, setFormError] = useState('')

  const { data: job, isLoading, isError, error } = useQuery<JobDetail>({
    queryKey: ['public-job', tenantSlug, jobSlug],
    queryFn: async () => {
      const r = await publicApi.get(`/api/v1/recruiter/careers/${tenantSlug}/${jobSlug}`)
      return r.data?.data ?? r.data
    },
    enabled: !!tenantSlug && !!jobSlug,
    retry: false,
  })

  const jobClosed = (error as any)?.response?.status === 410

  const readCvAsBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => {
        const result = reader.result as string
        // FileReader.readAsDataURL prefixes "data:application/pdf;base64,"
        // — backend expects raw base64 only (SubmitApplicationRequest.cvBase64
        // is documented as "base64-encoded PDF", not a data URI).
        const commaIndex = result.indexOf(',')
        resolve(commaIndex >= 0 ? result.slice(commaIndex + 1) : result)
      }
      reader.onerror = () => reject(reader.error)
      reader.readAsDataURL(file)
    })

  const apply = useMutation<ApplyResult, any, void>({
    mutationFn: async () => {
      let cvBase64: string | null = null
      if (cvFile) cvBase64 = await readCvAsBase64(cvFile)
      const r = await publicApi.post(
        `/api/v1/recruiter/careers/${tenantSlug}/jobs/${job!.id}/apply`,
        {
          firstName, lastName, email, phone: phone || null,
          location: location || null,
          linkedinUrl: linkedinUrl || null, portfolioUrl: portfolioUrl || null,
          cvBase64, cvFileName: cvFile?.name || null,
          source: 'CAREERS_PAGE',
        })
      return r.data?.data ?? r.data
    },
    onError: (e: any) => setFormError(e.response?.data?.message || 'Something went wrong submitting your application. Please try again.'),
  })

  const onPickFile = (file: File | null) => {
    setCvError('')
    if (!file) { setCvFile(null); return }
    if (file.type !== 'application/pdf') { setCvError('Please upload a PDF file.'); return }
    if (file.size > MAX_CV_BYTES) { setCvError('That file is too large — please keep your CV under 5MB.'); return }
    setCvFile(file)
  }

  const submit = () => {
    setFormError('')
    if (!firstName.trim() || !lastName.trim() || !email.trim()) {
      setFormError('Please fill in your first name, last name, and email.')
      return
    }
    apply.mutate()
  }

  const salaryLine = job?.showSalary && (job.salaryMin || job.salaryMax)
    ? [job.salaryMin, job.salaryMax].filter(Boolean).map(n => `R ${Number(n).toLocaleString()}`).join(' – ')
    : null

  if (apply.isSuccess && apply.data) {
    return (
      <div style={{ minHeight: '100vh', background: paper, fontFamily: "'Inter', -apple-system, sans-serif", display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
        <style>{fontImport}</style>
        <div style={{ maxWidth: 480, textAlign: 'center' as const }}>
          <div style={{ width: 56, height: 56, borderRadius: '50%', background: accentSoft, display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
            <CheckCircle2 size={26} color={accent} />
          </div>
          <h1 style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 28, margin: '0 0 12px', color: ink }}>
            Application received
          </h1>
          <p style={{ fontSize: 15, color: muted, lineHeight: 1.6, margin: '0 0 8px' }}>
            Thanks, {apply.data.applicantName.split(' ')[0]} — your application for <strong style={{ color: ink }}>{apply.data.jobTitle}</strong> at {apply.data.companyName} has been submitted.
          </p>
          <p style={{ fontSize: 14, color: muted, lineHeight: 1.6 }}>
            Check your email for a link to track your application status.
          </p>
          <Link to={`/careers/${tenantSlug}`} style={{ display: 'inline-block', marginTop: 16, fontSize: 14, color: accent, fontWeight: 500, textDecoration: 'none' }}>
            View other open positions
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div style={{ minHeight: '100vh', background: paper, fontFamily: "'Inter', -apple-system, sans-serif", color: ink }}>
      <style>{fontImport}</style>

      <header style={{ borderBottom: `1px solid ${line}`, padding: '20px 24px' }}>
        <div style={{ maxWidth: 720, margin: '0 auto' }}>
          <Link to={`/careers/${tenantSlug}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: muted, textDecoration: 'none' }}>
            <ArrowLeft size={14} /> All open positions
          </Link>
        </div>
      </header>

      <main style={{ maxWidth: 720, margin: '0 auto', padding: '40px 24px 100px' }}>
        {isLoading && <div style={{ color: muted, fontSize: 15 }}>Loading...</div>}

        {isError && !jobClosed && (
          <div style={{ padding: '48px 0', textAlign: 'center' as const }}>
            <div style={{ fontFamily: "'Fraunces', Georgia, serif", fontSize: 22, marginBottom: 8 }}>Position not found</div>
            <p style={{ color: muted, fontSize: 15 }}>This job posting may have been removed or the link is incorrect.</p>
            <Link to={`/careers/${tenantSlug}`} style={{ color: accent, fontSize: 14, fontWeight: 500 }}>View other open positions</Link>
          </div>
        )}

        {jobClosed && (
          <div style={{ padding: '48px 0', textAlign: 'center' as const }}>
            <div style={{ fontFamily: "'Fraunces', Georgia, serif", fontSize: 22, marginBottom: 8 }}>No longer accepting applications</div>
            <p style={{ color: muted, fontSize: 15 }}>This position isn't open anymore, but there may be other roles available.</p>
            <Link to={`/careers/${tenantSlug}`} style={{ color: accent, fontSize: 14, fontWeight: 500 }}>View other open positions</Link>
          </div>
        )}

        {job && (
          <>
            <div style={{ fontSize: 13, fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase', color: accent, marginBottom: 10 }}>
              {job.companyName}
            </div>
            <h1 style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 34, lineHeight: 1.15, margin: '0 0 16px' }}>
              {job.title}
            </h1>
            <div style={{ display: 'flex', flexWrap: 'wrap' as const, gap: '6px 20px', fontSize: 14, color: muted, marginBottom: 32 }}>
              {job.department && <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Briefcase size={14} /> {job.department}</span>}
              {job.location && <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><MapPin size={14} /> {job.location}</span>}
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Clock size={14} /> {JOB_TYPE_LABEL[job.jobType] ?? job.jobType}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><GraduationCap size={14} /> {EXPERIENCE_LABEL[job.experienceLevel] ?? job.experienceLevel}</span>
            </div>

            {salaryLine && (
              <div style={{ display: 'inline-block', background: accentSoft, color: accent, fontSize: 13, fontWeight: 600, padding: '6px 14px', borderRadius: 20, marginBottom: 32 }}>
                {salaryLine} / month
              </div>
            )}

            <div style={{ fontSize: 15, lineHeight: 1.7, whiteSpace: 'pre-wrap' as const, marginBottom: job.requirements || job.benefits ? 28 : 44 }}>
              {job.description}
            </div>

            {job.requirements && (
              <div style={{ marginBottom: 28 }}>
                <h3 style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 17, margin: '0 0 10px' }}>What you'll need</h3>
                <div style={{ fontSize: 15, lineHeight: 1.7, whiteSpace: 'pre-wrap' as const, color: ink }}>{job.requirements}</div>
              </div>
            )}

            {job.benefits && (
              <div style={{ marginBottom: 44 }}>
                <h3 style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 17, margin: '0 0 10px' }}>Benefits</h3>
                <div style={{ fontSize: 15, lineHeight: 1.7, whiteSpace: 'pre-wrap' as const, color: ink }}>{job.benefits}</div>
              </div>
            )}

            <div style={{ borderTop: `1px solid ${line}`, paddingTop: 36 }}>
              <h2 style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 24, margin: '0 0 24px' }}>
                Apply for this position
              </h2>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
                <div>
                  <label style={labelStyle}>First name *</label>
                  <input value={firstName} onChange={e => setFirstName(e.target.value)} style={inputStyle} placeholder="Thandiwe" />
                </div>
                <div>
                  <label style={labelStyle}>Last name *</label>
                  <input value={lastName} onChange={e => setLastName(e.target.value)} style={inputStyle} placeholder="Nkosi" />
                </div>
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={labelStyle}>Email *</label>
                <input type="email" value={email} onChange={e => setEmail(e.target.value)} style={inputStyle} placeholder="thandiwe@example.com" />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
                <div>
                  <label style={labelStyle}>Phone</label>
                  <input value={phone} onChange={e => setPhone(e.target.value)} style={inputStyle} placeholder="082 123 4567" />
                </div>
                <div>
                  <label style={labelStyle}>Location</label>
                  <input value={location} onChange={e => setLocation(e.target.value)} style={inputStyle} placeholder="Johannesburg" />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
                <div>
                  <label style={labelStyle}>LinkedIn</label>
                  <input value={linkedinUrl} onChange={e => setLinkedinUrl(e.target.value)} style={inputStyle} placeholder="linkedin.com/in/..." />
                </div>
                <div>
                  <label style={labelStyle}>Portfolio</label>
                  <input value={portfolioUrl} onChange={e => setPortfolioUrl(e.target.value)} style={inputStyle} placeholder="yoursite.com" />
                </div>
              </div>

              <div style={{ marginBottom: 28 }}>
                <label style={labelStyle}>CV (PDF)</label>
                <input ref={fileInputRef} type="file" accept="application/pdf" style={{ display: 'none' }}
                  onChange={e => onPickFile(e.target.files?.[0] ?? null)} />
                {cvFile ? (
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', border: `1px solid ${line}`, borderRadius: 9, padding: '11px 14px', background: '#fff' }}>
                    <span style={{ fontSize: 14, color: ink }}>{cvFile.name}</span>
                    <button type="button" onClick={() => onPickFile(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: muted, display: 'flex' }}>
                      <X size={16} />
                    </button>
                  </div>
                ) : (
                  <button type="button" onClick={() => fileInputRef.current?.click()}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 8, width: '100%', boxSizing: 'border-box',
                      padding: '13px 14px', border: `1px dashed ${line}`, borderRadius: 9, background: '#fff',
                      color: muted, fontSize: 14, cursor: 'pointer', fontFamily: 'inherit',
                    }}>
                    <Upload size={15} /> Upload your CV (optional, PDF only)
                  </button>
                )}
                {cvError && <div style={{ fontSize: 12, color: danger, marginTop: 6 }}>{cvError}</div>}
              </div>

              {formError && (
                <div style={{ background: dangerSoft, color: danger, fontSize: 13, padding: '10px 14px', borderRadius: 8, marginBottom: 18 }}>
                  {formError}
                </div>
              )}

              <button onClick={submit} disabled={apply.isPending}
                style={{
                  width: '100%', padding: '14px 0', background: accent, color: '#fff', border: 'none',
                  borderRadius: 9, fontSize: 15, fontWeight: 600, cursor: apply.isPending ? 'default' : 'pointer',
                  opacity: apply.isPending ? 0.7 : 1, fontFamily: 'inherit',
                }}>
                {apply.isPending ? 'Submitting...' : 'Submit application'}
              </button>
            </div>
          </>
        )}
      </main>
    </div>
  )
}
