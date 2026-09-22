import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from './app/App'
import { reportBrowserError, startBrowserErrors } from './lib/browser-errors'
import './index.css'

void startBrowserErrors()
const reportReactError = (error: unknown) => { reportBrowserError(error); console.error(error) }
createRoot(document.getElementById('root')!, {
  onUncaughtError: reportReactError, onCaughtError: reportReactError, onRecoverableError: reportReactError,
}).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
