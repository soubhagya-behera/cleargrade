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
        className="cg-btn-secondary !min-h-0 disabled:opacity-50"
      >
        Previous
      </button>
      {pages.map((index) => (
        <button
          key={index}
          type="button"
          aria-current={index === page ? 'page' : undefined}
          onClick={() => onPageChange(index)}
          className={`inline-flex min-h-10 items-center justify-center rounded-md px-3 text-sm font-medium ${index === page ? 'bg-brand-600 text-white' : 'cg-btn-secondary !min-h-0'}`}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="cg-btn-secondary !min-h-0 disabled:opacity-50"
      >
        Next
      </button>
    </nav>
  );
}
