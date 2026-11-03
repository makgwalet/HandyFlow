import { useState } from 'react'
import { useAuth } from '@/hooks/use-auth'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Activity } from 'lucide-react'

export function Login() {
  const [email, setEmail] = useState('admin@clinic.co.za')
  const [password, setPassword] = useState('password')
  const [isLoading, setIsLoading] = useState(false)
  const { login } = useAuth()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    try { await login(email, password) } finally { setIsLoading(false) }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="w-full max-w-md rounded-2xl border border-medical-border bg-white p-8 shadow-lg">
        <div className="mb-8 flex flex-col items-center">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-600 text-white">
            <Activity className="h-7 w-7" />
          </div>
          <h1 className="text-2xl font-bold text-medical-text">HandyFlow Clinic</h1>
          <p className="mt-1 text-sm text-medical-muted">Sign in to your practice</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Input label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Button type="submit" className="w-full" isLoading={isLoading}>Sign in</Button>
        </form>
        <p className="mt-6 text-center text-xs text-medical-muted">Offline mode available. Data syncs automatically when connection is restored.</p>
      </div>
    </div>
  )
}