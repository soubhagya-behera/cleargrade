# ClearGrade — UX Specification

Status: **Design documentation only — no screens are implemented.** Wireframes are in the assignment PDF (pp. 4–5); their numbers are illustrative, not data to reproduce. `[REQ]`

## 1. Product design direction

Calm, premium and restrained — an information tool, not a marketing site. The administrator's primary question is always "what happened to my file, and why was this row rejected?" Every screen answers it in plain language before technical detail. Design priorities: **clarity of explanation, legible density, and trustworthy states**. Prefer working states over decoration; no gratuitous charts or animation. Bonus affordances are visibly tagged and never block the core flow (D-17).

## 2. Design system tokens

**Colour** (AA contrast, status never colour-only — always pair with word or icon):

| Role | Token | Use |
|---|---|---|
| Primary | violet `#6D4AFF` | Active nav, primary CTA, links, progress |
| Text | slate `#1F2440` | Headings/body |
| Muted | `#6B7190` | Captions, helper text |
| Surface / canvas | `#FFFFFF` / `#F7F8FC` | Cards / page |
| Border | `#E4E7F0` | Tables, inputs, cards |
| Success / Danger | `#1E8E5A` / `#C0392B` | Imported/rejected counts, PASS/FAIL |
| Bonus | amber `#E0A800` | `BONUS` pill |

**Spacing scale (4px base):** `4 / 8 / 12 / 16 / 24 / 32 / 48` px. Consistent gutters (24–32px) and card padding (24px).

**Typography hierarchy:** Display `32/700`; H1 `28/700`; H2 `20/600`; Body `16/400`; Table `14/400`; Caption `12/500`. Font: system UI stack (`Inter` if available, fallback `-apple-system, Segoe UI, Roboto, sans-serif`). Tabular numerals for numeric columns. Heading line-height 1.2, body 1.5.

**Elevation:** `e0` flat (page sections), `e1` subtle border + 1px shadow (cards/tables), `e2` shadow for overlays only. No heavy drop shadows.

**Component tokens:** radius `8px` (cards/buttons), `4px` (tags); border `1px solid #E4E7F0`; focus ring `2px #6D4AFF` offset `2px`; interactive transition `120ms` ease for colour/border only; minimum tap target `44px`.

## 3. Layout and navigation

- Persistent 64px top bar: brand mark + "Results Portal"; links **Import** and **Students**, underline on active route.
- Centred content column, max width ~1200px, spacing scale gutters.
- Routes via **React Router**: `/import`, `/students`, `/students/:id`. Browser URL is shareable; report links open directly. **TanStack Query** owns server state (loading/error/retry); local component state handles forms and UI. No Redux.
- `AppShell` provides `<main>`, skip link, routed content; no sidebar in v1.

## 4. Component inventory

`AppShell`, `TopNav`, `PageHeader`, `FileDropzone`, `FileSummary`, `PrimaryButton`, `StatCard`, `DataQualitySummary`, `ProblemTable`, `Pagination`, `SearchInput`, `FilterSelect`, `StudentTable`, `ResultBadge`, `MarkBar`, `Avatar`, `InfoCard`, `EmptyState`, `ErrorState`, `SkeletonTable`, `NotFoundPanel`, `BonusPill`, `Toast`.

## 5. Import workspace (`/import`)

1. **Header** — "Import student results"; subtitle: valid rows are imported, problem rows are listed below.
2. **Upload zone** — dashed "Drag and drop a CSV file here", "or", "Choose file". On selection show filename + size and required-column list. Configurable 10 MiB limit is communicated on rejection (U-06).
3. **Action** — "Upload and import", disabled until a file is chosen; busy state during upload.
4. **Data-quality summary** — prominent `DataQualitySummary` panel between the action and the problem table. It states, in administrator language: rows read; imported (split **N new, M changed, K unchanged** per U-02); rows rejected; and the number of **problems found** (error occurrences, D-10), with a short breakdown by code. It makes explicit that "X rows rejected" and "Y problems found" are different numbers. No chart — a compact, typographic summary is clearer and more restrained.
5. **StatCards** — three cards: *Rows read*, *Imported* (sub-line new/changed/unchanged), *Rows rejected* — consistent with the summary panel.
6. **Row problems** — `ProblemTable` columns **Row | Student ID | Field | Problem**. Friendly message first, code as muted tag. Physical line numbers, `(blank)` for missing IDs, `(whole row)` for field-count errors. **Page size 50** (T-4), with a code filter. `BONUS` error-CSV download deferred (D-17).
7. **Failure states** — file-level errors show an inline `ErrorState` (missing columns, size, encoding) with "Choose another file"; app stays on screen. `409 BATCH_PROCESSING` shows "This file is already being imported" with the batch reference.

## 6. Student directory (`/students`)

1. **Header** — "Students".
2. **Controls** — `SearchInput` "Search by name or student ID" (debounced, server-side); class `FilterSelect` is BONUS (D-17).
3. **Summary line** — "Showing 1 to 25 of 1,000 students" (page size 25, T-4).
4. **Table** — `Student ID | Name | Class | Section | Percentage | Grade`. Name links to `/students/:id`; percentage/grade from D-07. Stable ID sort; optional column sorting.
5. **Pagination** — Previous / numbered pages / Next, rows-per-page selector (default 25, max 100).
6. **States** — loading skeleton rows; empty ("No students match 'xyz'" + **Clear search**); error (friendly message + **Retry**); zero students ("No students imported yet" + link to `/import`).

## 7. Student report (`/students/:id`)

1. **Back link** — "‹ Back to students", preserving prior search/page where practical.
2. **Identity card** — initials `Avatar`, full name, `ID | Class N, Section X`, email, "Date of birth: 1 January 2010" (human-readable long date).
3. **Overall card** — large percentage, letter grade, PASS/FAIL `ResultBadge`, "Total: 392 / 500". PASS follows overall percentage ≥ 50; no per-subject indicator (D-07).
4. **Marks by subject** — five subjects with marks out of 100 and optional `MarkBar` (BONUS, deferred); total 392 / 500. Labels capitalised ("Second language"). Mark precision is preserved (C-3); display uses a restrained one-decimal presentation without implying rounding of stored values.
5. **Not found** — `NotFoundPanel` with clear message and link back (unknown ID is a normal case).
6. **BONUS** "Print report" deferred (D-17).

## 8. Responsive behaviour

- Desktop (>1024px): full tables; identity/overall cards side by side.
- Tablet (640–1024px): cards stack; tables keep columns with horizontal scroll.
- Mobile (<640px): nav wraps; stat cards stack; tables horizontally scrollable with a visible cue; actions full-width tap targets.
- Touch targets ≥44px; no hover-only interactions.

## 9. Loading, empty, success and error states

| Context | State | Behaviour |
|---|---|---|
| Upload | Loading | Button spinner, dropzone disabled, progress message |
| Upload | Success | Quality summary + stats + problems table inline; no page reload |
| Upload | Empty file | Inline error with reason and retry |
| Upload | Already processing | `409` message with batch reference |
| Directory | Loading | `SkeletonTable` preserving row height |
| Directory | Empty | Message echoing query + Clear search |
| Directory | Error | "Could not load students" + Retry |
| Report | Loading | Card skeletons |
| Report | Not found | `NotFoundPanel` + link back |
| Global | Unexpected | Friendly toast with correlation id for support |

## 10. Accessibility

- Semantic landmarks (`header`, `main`, `nav`), one `h1` per page, logical heading order.
- Visible labels for every input; accessible names for search/filters.
- `<th scope="col">` on tables; row numbers and codes are real text, not colour-only cues.
- `aria-live="polite"` for import outcome and result counts.
- Visible `:focus-visible` rings, full keyboard operation, skip-to-content link.
- WCAG AA contrast; text alternatives for icon-only controls; errors linked via `aria-describedby`.

## 11. Access note

No authentication is implemented (U-03). This UI is for local/trusted evaluation only and must not be publicly exposed without authentication.
