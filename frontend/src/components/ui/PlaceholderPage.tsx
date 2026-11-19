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
      <p className="text-xs font-semibold uppercase tracking-wider text-brand-700">{eyebrow}</p>
      <h2 id="page-heading" className="mt-2 text-2xl font-semibold tracking-tight sm:text-3xl">
        {title}
      </h2>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed sm:text-base">{description}</p>

      <div className="mt-8 rounded-2xl border border-line bg-surface p-8 shadow-sm">
        <div className="flex size-12 items-center justify-center rounded-xl bg-brand-100 text-brand-700">
          <Icon aria-hidden="true" className="size-6" />
        </div>
        <h3 className="mt-5 text-base font-semibold">Foundation phase</h3>
        <p className="mt-2 max-w-xl text-sm text-ink-500">
          This screen is a structural placeholder. No data is shown and no import is performed yet.
        </p>

        <h4 className="mt-6 text-xs font-semibold uppercase tracking-wider text-ink-500">Planned for this screen</h4>
        <ul className="mt-3 grid gap-2 text-sm text-ink-700 sm:grid-cols-2">
          {planned.map((item) => (
            <li key={item} className="flex items-start gap-2">
              <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-brand-600" />
              {item}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
