import DevDemoApp from './DevDemoApp'
import { DevDemoRoutePage } from './DevDemoRoutePage'

/** Mounted only from the DEV-gated `/dev/demo` route (tree-shaken from production). */
export function DevDemoLazyRoute() {
  return (
    <>
      <DevDemoRoutePage />
      <DevDemoApp />
    </>
  )
}
