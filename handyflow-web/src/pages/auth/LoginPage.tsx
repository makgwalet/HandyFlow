// src/pages/auth/LoginPage.tsx

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'
import { authApi } from '../../api/auth.api'
import { useAuthStore } from '../../store/auth.store'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import type { User } from '../../types/auth.types'

const schema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
  tenantSlug: z.string().min(1, 'Company slug is required'),
})

type FormData = z.infer<typeof schema>

export function LoginPage() {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const { mutate: login, isPending, error } = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      const user: User = {
        userId: data.userId,
        tenantId: data.tenantId,
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        permissions: data.permissions,
      }
      setAuth(data.accessToken, user)
      navigate('/dashboard')
    },
  })

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#1B3A6B] to-[#0D9488] flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-white/10 mb-4">
            <Building2 className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white">HandyFlow</h1>
          <p className="text-blue-200 mt-1">Business Operating System</p>
        </div>

        {/* Form */}
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          <h2 className="text-xl font-semibold text-gray-900 mb-6">Sign in to your account</h2>

          <form onSubmit={handleSubmit((data) => login(data))} className="space-y-4">
            <Input
              label="Company Slug"
              placeholder="acme-security"
              error={errors.tenantSlug?.message}
              {...register('tenantSlug')}
            />
            <Input
              label="Email address"
              type="email"
              placeholder="you@company.co.za"
              error={errors.email?.message}
              {...register('email')}
            />
            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              error={errors.password?.message}
              {...register('password')}
            />

            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                Invalid credentials. Please try again.
              </div>
            )}

            <Button type="submit" className="w-full" size="lg" loading={isPending}>
              Sign in
            </Button>
          </form>

          <p className="mt-4 text-center text-sm text-gray-500">
            Don't have an account?{' '}
            <a href="/register" className="text-blue-600 font-medium hover:underline">
              Start your free pilot
            </a>
          </p>
        </div>
      </div>
    </div>
  )
}