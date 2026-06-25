import { Link, useNavigate } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { Card, CardDescription, CardTitle } from '../../../components/ui/card'

export function SignInPage() {
  const navigate = useNavigate()

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <Card className="w-full max-w-md">
        <CardTitle>Sign in</CardTitle>
        <CardDescription>
          Auth UI ships in #49. This route reserves the production sign-in flow and layout shell.
        </CardDescription>
        <div className="mt-6 flex flex-col gap-3">
          <Button onClick={() => navigate('/app')}>Continue to app shell (placeholder)</Button>
          <Link className="text-center text-sm text-app-muted hover:text-app-text" to="/">
            Back to landing
          </Link>
        </div>
      </Card>
    </div>
  )
}
