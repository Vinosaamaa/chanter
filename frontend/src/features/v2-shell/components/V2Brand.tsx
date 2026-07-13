import { Link } from 'react-router-dom'

type V2BrandProps = {
  to?: string
  className?: string
}

export function V2Brand({ to, className = '' }: V2BrandProps) {
  const content = (
    <>
      <span className="v2-brand-mark" aria-hidden="true">
        <i />
        <i />
      </span>
      <strong>Chanter</strong>
    </>
  )

  if (to) {
    return (
      <Link className={`v2-brand ${className}`.trim()} to={to} aria-label="Chanter home">
        {content}
      </Link>
    )
  }

  return <div className={`v2-brand ${className}`.trim()}>{content}</div>
}
