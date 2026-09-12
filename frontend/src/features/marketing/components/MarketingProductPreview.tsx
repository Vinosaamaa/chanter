import { BookOpen, MessageSquare, CalendarDays } from 'lucide-react'

/** An explicitly illustrative Course, never presented as live product activity. */
export function MarketingProductPreview() {
  return (
    <aside className="marketing-product-preview" aria-label="Product preview">
      <div className="mpp-example-label">An example Course in Chanter</div>
      <article className="mpp-course-cover">
        <BookOpen aria-hidden="true" />
        <p>Foundations of computer science</p>
        <h2>Big ideas.<br />Small first steps.</h2>
        <span>A place to learn, practice and ask.</span>
      </article>
      <div className="mpp-conversation">
        <MessageSquare aria-hidden="true" />
        <div><h3>Where should I begin?</h3><p>Questions stay beside the materials and people who can help.</p></div>
      </div>
      <div className="mpp-office"><CalendarDays aria-hidden="true" /><span>Make time to work it through together.</span></div>
    </aside>
  )
}
