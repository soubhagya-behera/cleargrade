import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <section aria-labelledby="not-found-heading" className="rounded-2xl border border-line bg-surface p-8">
      <p className="text-xs font-semibold uppercase tracking-wider text-brand-700">404</p>
      <h2 id="not-found-heading" className="mt-2 text-2xl font-semibold">
        Page not found
      </h2>
      <p className="mt-3 text-sm">The requested page does not exist.</p>
      <p className="mt-6 text-sm">
        <Link to="/import" className="font-medium text-brand-700 underline underline-offset-4 hover:text-brand-600">
          Go to Import Quality Center
        </Link>
      </p>
    </section>
  );
}
