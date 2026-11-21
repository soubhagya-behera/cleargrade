import type { LucideIcon } from 'lucide-react';

interface PlaceholderPageProps {
  eyebrow: string;
  title: string;
  description: string;
  icon: LucideIcon;
  planned: string[];
}

export function PlaceholderPage({ eyebrow, title, description, icon: Icon, planned }: PlaceholderPageProps) {
  return (
    <section aria-labelledby="page-heading">
      <p className="cg-eyebrow">{eyebrow}</p>
      <h2 id="page-heading" className="cg-page-title">{title}</h2>
      <p className="mt-5 max-w-2xl text-sm leading-relaxed text-ink-700 sm:text-base">{description}</p>

      <div className="cg-card cg-rise-1 mt-8 p-8">
        <div className="flex size-12 items-center justify-center rounded-2xl bg-lake-100 text-lake-700">
          <Icon aria-hidden="true" className="size-6" />
        </div>
        <h3 className="mt-5 text-base font-bold text-navy-900">Foundation phase</h3>
        <p className="mt-2 max-w-xl text-sm text-ink-500">
          This screen is a structural placeholder. No data is shown and no import is performed yet.
        </p>

        <h4 className="cg-label mt-6">Planned for this screen</h4>
        <ul className="mt-3 grid gap-2 text-sm text-ink-700 sm:grid-cols-2">
          {planned.map((item) => (
            <li key={item} className="flex items-start gap-2">
              <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-sun-500" />
              {item}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
