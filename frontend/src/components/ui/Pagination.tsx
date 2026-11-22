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
    <nav aria-label={label} className="flex flex-wrap items-center gap-2">
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        className="cg-btn-secondary !min-h-10 !px-4 disabled:opacity-50"
      >
        Previous
      </button>
      {pages.map((index) => (
        <button
          key={index}
          type="button"
          aria-current={index === page ? 'page' : undefined}
          onClick={() => onPageChange(index)}
          className={`inline-flex min-h-10 min-w-10 cursor-pointer items-center justify-center rounded-xl px-3 text-sm font-semibold transition-all duration-200 ${
            index === page
              ? 'bg-navy-900 text-white shadow-md shadow-navy-900/20'
              : 'border border-line bg-surface text-ink-700 hover:border-lake-500 hover:bg-lake-050 hover:text-navy-900'
          }`}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="cg-btn-secondary !min-h-10 !px-4 disabled:opacity-50"
      >
        Next
      </button>
    </nav>
  );
}
