import { cn } from '@/lib/utils'
import { forwardRef, type InputHTMLAttributes } from 'react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  helper?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, helper, className, ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="mb-1.5 block text-sm font-medium text-medical-text">
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={cn(
            'block w-full rounded-lg border bg-white px-3 py-2 text-sm transition-colors placeholder:text-medical-muted focus:outline-none focus:ring-1',
            error
              ? 'border-rose-300 focus:border-rose-500 focus:ring-rose-500'
              : 'border-medical-border focus:border-primary-500 focus:ring-primary-500',
            className
          )}
          {...props}
        />
        {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
        {helper && !error && <p className="mt-1 text-xs text-medical-muted">{helper}</p>}
      </div>
    )
  }
)
Input.displayName = 'Input'
