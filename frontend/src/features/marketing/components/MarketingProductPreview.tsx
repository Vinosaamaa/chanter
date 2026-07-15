import { Link } from 'react-router-dom'

import { MARKETING_SIGN_IN_PATH } from '../marketing-routes'

/** Inline product chrome for landing (#104) — TA queue, course stats, Join Queue CTA. */
export function MarketingProductPreview() {
  return (
    <aside className="marketing-product-preview" aria-label="Product preview">
      <div className="mpp-chrome">
        <div className="mpp-rail" aria-hidden="true">
          <span /><span /><span />
        </div>
        <div className="mpp-main">
          <header className="mpp-top">
            <strong>CS 101 — Intro to CS</strong>
            <span>Questions</span>
          </header>
          <div className="mpp-body">
            <section className="mpp-stats" aria-label="Course stats">
              <div><b>128</b><small>Learners</small></div>
              <div><b>14</b><small>Open questions</small></div>
              <div><b>6</b><small>Office hours</small></div>
            </section>
            <section className="mpp-queue" aria-label="TA queue preview">
              <div className="mpp-queue-head">
                <h3>TA queue</h3>
                <span>3 waiting</span>
              </div>
              <ul>
                <li><span>Sam L.</span><em>Spring Security filters?</em></li>
                <li><span>Jordan K.</span><em>Homework deadline</em></li>
                <li><span>Riley P.</span><em>Office hours today?</em></li>
              </ul>
              <Link className="mpp-join" to={MARKETING_SIGN_IN_PATH}>
                Join Queue
              </Link>
            </section>
          </div>
        </div>
      </div>
    </aside>
  )
}
