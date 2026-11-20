interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  label: string;
}

export function Pagination({ page, totalPages, onPageChange, label }: PaginationProps) {
  if (totalPages <= 1) return null;
  const pages = Array.from({ length: Math.min(totalPages, 7) }, (_, index) => index);
  return (
    <nav aria-label={label} className="mt-4 flex flex-wrap items-center gap-2">
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        className="rounded-md border border-line bg-surface px-3 py-2 text-sm disabled:opacity-50"
      >
        Previous
      </button>
      {pages.map((index) => (
        <button
          key={index}
          type="button"
          aria-current={index === page ? 'page' : undefined}
          onClick={() => onPageChange(index)}
          className={`rounded-md px-3 py-2 text-sm ${index === page ? 'bg-brand-600 text-white' : 'border border-line bg-surface'}`}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="rounded-md border border-line bg-surface px-3 py-2 text-sm disabled:opacity-50"
      >
        Next
      </button>
    </nav>
  );
}
