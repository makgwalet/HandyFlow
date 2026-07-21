import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { MapPin, Briefcase, Clock, ArrowRight } from 'lucide-react'

// Deliberately separate from the staff app's authenticated apiClient —
// these are public, unauthenticated endpoints (no @PreAuthorize on the
// backend), and mixing a stale/absent bearer token into the same client
// used for staff auth risks pulling in interceptor behavior (401 redirects
// etc.) that makes no sense on a page nobody is logged into. Same
// separation the accountant client portal already uses (portal.client.ts).
//
// baseURL matches apiClient's own (src/api/client.ts) exactly — without
// it, relative paths resolve against the Vite dev server's own origin
// (localhost:5173) instead of the backend (localhost:8080), which is a
// real bug that was confirmed happening (jobs came back as an HTML
// string, not JSON, and .filter threw on it). No withCredentials and no
// auth-token interceptor here — this client never carries a session.
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

interface Job {
  id: string; title: string; department: string | null; location: string | null
  jobType: string; experienceLevel: string; description: string
  salaryMin: number | null; salaryMax: number | null; showSalary: boolean
  slug: string; companyName: string | null; createdAt: string
}

const JOB_TYPE_LABEL: Record<string, string> = {
  FULL_TIME: 'Full-time', PART_TIME: 'Part-time', CONTRACT: 'Contract',
  INTERNSHIP: 'Internship', FREELANCE: 'Freelance',
}

const paper = '#FAF8F4'
const ink = '#1C1A16'
const muted = '#736C5E'
const line = '#E7E2D6'
const accent = '#0F5138'
const accentSoft = '#E6F0EA'

const fontImport = `@import url('https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600&family=Inter:wght@400;500;600&display=swap');`

export function CareersListPage() {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const [query, setQuery] = useState('')

  const { data: jobs, isLoading, isError } = useQuery<Job[]>({
    queryKey: ['public-careers', tenantSlug],
    queryFn: async () => {
      const r = await publicApi.get(`/api/v1/recruiter/careers/${tenantSlug}`)
      return r.data?.data ?? r.data
    },
    enabled: !!tenantSlug,
  })

  const companyName = jobs?.[0]?.companyName ?? tenantSlug
  const filtered = (jobs ?? []).filter(j =>
    query === '' ||
    j.title.toLowerCase().includes(query.toLowerCase()) ||
    (j.department ?? '').toLowerCase().includes(query.toLowerCase()))

  return (
    <div style={{ minHeight: '100vh', background: paper, fontFamily: "'Inter', -apple-system, sans-serif", color: ink }}>
      <style>{fontImport}</style>

      <header style={{ borderBottom: `1px solid ${line}`, padding: '48px 24px 40px' }}>
        <div style={{ maxWidth: 760, margin: '0 auto' }}>
          <div style={{ fontSize: 12, fontWeight: 600, letterSpacing: '0.08em', textTransform: 'uppercase', color: accent, marginBottom: 10 }}>
            Careers
          </div>
          <h1 style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 40, lineHeight: 1.15, margin: 0, color: ink }}>
            {companyName}
          </h1>
          <p style={{ fontSize: 16, color: muted, marginTop: 12, marginBottom: 0 }}>
            {isLoading ? 'Loading open positions...' : `${filtered.length} open position${filtered.length === 1 ? '' : 's'}`}
          </p>
        </div>
      </header>

      <main style={{ maxWidth: 760, margin: '0 auto', padding: '40px 24px 80px' }}>
        {(jobs?.length ?? 0) > 3 && (
          <input
            value={query} onChange={e => setQuery(e.target.value)}
            placeholder="Search by title or department"
            style={{
              width: '100%', boxSizing: 'border-box', padding: '12px 16px', fontSize: 15,
              border: `1px solid ${line}`, borderRadius: 10, marginBottom: 28,
              background: '#fff', color: ink, fontFamily: 'inherit',
            }}
          />
        )}

        {isError && (
          <div style={{ padding: '32px 0', color: muted, fontSize: 15 }}>
            This careers page isn't available right now. If you followed a link here, double-check it's correct.
          </div>
        )}

        {!isLoading && !isError && filtered.length === 0 && (
          <div style={{ padding: '48px 0', textAlign: 'center' as const }}>
            <div style={{ fontFamily: "'Fraunces', Georgia, serif", fontSize: 22, marginBottom: 8, color: ink }}>
              {query ? 'No matching positions' : 'No open positions right now'}
            </div>
            <p style={{ color: muted, fontSize: 15, margin: 0 }}>
              {query ? 'Try a different search term.' : "Check back soon — new roles are posted here as they open."}
            </p>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column' as const, gap: 12 }}>
          {filtered.map(job => (
            <Link key={job.id} to={`/careers/${tenantSlug}/${job.slug}`}
              style={{ textDecoration: 'none', color: 'inherit' }}>
              <div style={{
                background: '#fff', border: `1px solid ${line}`, borderRadius: 12,
                padding: '22px 24px', display: 'flex', justifyContent: 'space-between',
                alignItems: 'center', gap: 16, transition: 'border-color 0.15s',
              }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontFamily: "'Fraunces', Georgia, serif", fontWeight: 500, fontSize: 20, marginBottom: 6, color: ink }}>
                    {job.title}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap' as const, gap: '4px 16px', fontSize: 13, color: muted }}>
                    {job.department && (
                      <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                        <Briefcase size={13} /> {job.department}
                      </span>
                    )}
                    {job.location && (
                      <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                        <MapPin size={13} /> {job.location}
                      </span>
                    )}
                    <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                      <Clock size={13} /> {JOB_TYPE_LABEL[job.jobType] ?? job.jobType}
                    </span>
                  </div>
                </div>
                <div style={{
                  flexShrink: 0, width: 36, height: 36, borderRadius: '50%', background: accentSoft,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <ArrowRight size={16} color={accent} />
                </div>
              </div>
            </Link>
          ))}
        </div>
      </main>

      <footer style={{ borderTop: `1px solid ${line}`, padding: '24px', textAlign: 'center' as const }}>
        <span style={{ fontSize: 12, color: muted }}>Powered by HandyFlow</span>
      </footer>
    </div>
  )
}
