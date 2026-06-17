import { useEffect, useState } from 'react'
import './App.css'

type HealthResponse = {
  status: string
  service?: string
}

function App() {
  const [gatewayHealth, setGatewayHealth] = useState<string>('checking...')
  const [authHealth, setAuthHealth] = useState<string>('checking...')

  useEffect(() => {
    fetch('/actuator/health')
      .then((response) => response.json())
      .then((data) => setGatewayHealth(data.status ?? 'unknown'))
      .catch(() => setGatewayHealth('unreachable'))

    fetch('/api/v1/auth/health')
      .then((response) => response.json())
      .then((data: HealthResponse) => setAuthHealth(data.status ?? 'unknown'))
      .catch(() => setAuthHealth('unreachable'))
  }, [])

  return (
    <main className="app">
      <header>
        <p className="eyebrow">Chanter</p>
        <h1>Education-first learning community</h1>
        <p className="lede">
          Monorepo bootstrap is running. The frontend proxies API calls through the gateway.
        </p>
      </header>

      <section className="status-grid">
        <article className="status-card">
          <h2>Gateway</h2>
          <p className={`status ${gatewayHealth}`}>{gatewayHealth}</p>
          <code>/actuator/health</code>
        </article>
        <article className="status-card">
          <h2>Auth Service</h2>
          <p className={`status ${authHealth}`}>{authHealth}</p>
          <code>/api/v1/auth/health</code>
        </article>
      </section>
    </main>
  )
}

export default App
