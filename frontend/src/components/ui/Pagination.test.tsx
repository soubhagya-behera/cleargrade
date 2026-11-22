import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  it('keeps the current page highlighted and reachable beyond the first seven pages', () => {
    const onPageChange = vi.fn();
    render(<Pagination page={7} totalPages={10} onPageChange={onPageChange} label="Import history pages" />);
    const nav = screen.getByRole('navigation', { name: 'Import history pages' });

    expect(within(nav).getByRole('button', { name: '8' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('button', { name: '10' })).toBeInTheDocument();
    expect(within(nav).queryByRole('button', { name: '1' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Previous' })).toBeEnabled();
    expect(within(nav).getByRole('button', { name: 'Next' })).toBeEnabled();

    fireEvent.click(within(nav).getByRole('button', { name: '10' }));
    expect(onPageChange).toHaveBeenCalledWith(9);
    fireEvent.click(within(nav).getByRole('button', { name: 'Next' }));
    expect(onPageChange).toHaveBeenCalledWith(8);
  });

  it('clamps the visible window at the first and last pages', () => {
    const { unmount } = render(<Pagination page={0} totalPages={10} onPageChange={vi.fn()} label="First pages" />);
    let nav = screen.getByRole('navigation', { name: 'First pages' });
    expect(within(nav).getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('button', { name: '7' })).toBeInTheDocument();
    expect(within(nav).queryByRole('button', { name: '8' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Previous' })).toBeDisabled();
    unmount();

    render(<Pagination page={9} totalPages={10} onPageChange={vi.fn()} label="Last pages" />);
    nav = screen.getByRole('navigation', { name: 'Last pages' });
    expect(within(nav).getByRole('button', { name: '10' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('button', { name: '4' })).toBeInTheDocument();
    expect(within(nav).queryByRole('button', { name: '3' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('hides the controls when there is at most one page', () => {
    const { unmount } = render(<Pagination page={0} totalPages={1} onPageChange={vi.fn()} label="One page" />);
    expect(screen.queryByRole('navigation', { name: 'One page' })).not.toBeInTheDocument();
    unmount();
    render(<Pagination page={0} totalPages={0} onPageChange={vi.fn()} label="No pages" />);
    expect(screen.queryByRole('navigation', { name: 'No pages' })).not.toBeInTheDocument();
  });
});
