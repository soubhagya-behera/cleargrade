import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';

export function NotFoundPage() {
  return (
    <section aria-labelledby="not-found-heading" className="cg-card cg-rise-1 p-8 text-center sm:p-12">
      <span className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-lake-100 text-lake-700">
        <Compass aria-hidden="true" className="size-7" />
      </span>
      <p className="cg-eyebrow mt-6">404</p>
      <h2 id="not-found-heading" className="mt-2 text-2xl font-bold tracking-tight text-navy-900">
        Page not found
      </h2>
      <p className="mt-3 text-sm">The requested page does not exist.</p>
      <p className="mt-6 text-sm">
        <Link to="/import" className="font-semibold text-lake-700 underline decoration-lake-500/50 underline-offset-4 transition-colors hover:text-navy-900">
          Go to Import Quality Center
        </Link>
      </p>
    </section>
  );
}
