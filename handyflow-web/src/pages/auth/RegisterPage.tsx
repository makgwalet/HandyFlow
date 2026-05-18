// src/pages/auth/RegisterPage.tsx

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Building2, CheckCircle } from 'lucide-react'
import { authApi } from '../../api/auth.api'
import { useAuthStore } from '../../store/auth.store'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import type { User } from '../../types/auth.types'

const schema = z.object({
  companyName: z.string().min(2, 'Company name must be at least 2 characters'),
  slug: z.string()
    .min(3, 'Slug must be at least 3 characters')
    .regex(/^[a-z0-9-]+$/, 'Lowercase letters, numbers and hyphens only'),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().email('Invalid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
})

type FormData = z.infer<typeof schema>

export function RegisterPage() {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const { mutate, isPending, error } = useMutation({
    mutationFn: authApi.register,
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
      <div className="w-full max-w-lg">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-white/10 mb-4">
            <Building2 className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white">Start your free pilot</h1>
          <p className="text-blue-200 mt-1">60 days free — no credit card required</p>
        </div>

        {/* Benefits */}
        <div className="grid grid-cols-3 gap-3 mb-6">
          {['60-day pilot', 'Full access', 'No card needed'].map((benefit) => (
            <div key={benefit} className="bg-white/10 rounded-lg px-3 py-2 flex items-center gap-2">
              <CheckCircle className="w-4 h-4 text-[#0D9488] flex-shrink-0" />
              <span className="text-white text-xs font-medium">{benefit}</span>
            </div>
          ))}
        </div>

        <div className="bg-white rounded-2xl shadow-2xl p-8">
          <h2 className="text-xl font-semibold text-gray-900 mb-6">Create your company account</h2>

          <form onSubmit={handleSubmit((data) => mutate(data))} className="space-y-4">
            <Input
              label="Company Name"
              placeholder="Acme Security Solutions"
              error={errors.companyName?.message}
              {...register('companyName')}
            />
            <Input
              label="Company Slug"
              placeholder="acme-security"
              hint="Used to identify your company on login. Lowercase, hyphens only."
              error={errors.slug?.message}
              {...register('slug')}
            />
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="First Name"
                placeholder="John"
                error={errors.firstName?.message}
                {...register('firstName')}
              />
              <Input
                label="Last Name"
                placeholder="Doe"
                error={errors.lastName?.message}
                {...register('lastName')}
              />
            </div>
            <Input
              label="Work Email"
              type="email"
              placeholder="john@acme-security.co.za"
              error={errors.email?.message}
              {...register('email')}
            />
            <Input
              label="Password"
              type="password"
              placeholder="At least 8 characters"
              error={errors.password?.message}
              {...register('password')}
            />

            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
                Registration failed. The slug or email may already be taken.
              </div>
            )}

            <Button type="submit" className="w-full" size="lg" loading={isPending}>
              Start free pilot
            </Button>
          </form>

          <p className="mt-4 text-center text-sm text-gray-500">
            Already have an account?{' '}
            <a href="/login" className="text-blue-600 font-medium hover:underline">
              Sign in
            </a>
          </p>
        </div>
      </div>
    </div>
  )
}