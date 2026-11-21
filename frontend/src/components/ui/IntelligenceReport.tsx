import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import { Info } from 'lucide-react';
import type {
  AiStatusResponse,
  InsightFailureResponse,
  InsightFindingResponse,
  InsightResponse,
  InsightSeverity,
} from '../../types/api';

const ISSUE_CONTENT: Record<string, { heading: string; description: string }> = {
  INVALID_FORMAT_COUNT: {
    heading: 'Invalid formats',
    description: 'Some values, such as dates or email addresses, do not match the expected format.',
  },
  MISSING_REQUIRED_COUNT: {
    heading: 'Missing required information',
    description: 'Some required fields have been left blank.',
  },
  OUT_OF_RANGE_COUNT: {
    heading: 'Values outside allowed limits',
    description: 'Some marks or other values are outside the permitted range.',
  },
  DUPLICATE_ID_COUNT: {
    heading: 'Duplicate student ID',
    description: 'A student ID appears more than once.',
  },
  WRONG_FIELD_COUNT_COUNT: {
    heading: 'Incorrect number of columns',
    description: 'A row contains too many or too few values.',
  },
  MULTIPLE_ERRORS_PER_REJECTED_ROW: {
    heading: 'Multiple issues on one row',
    description: 'Some rejected rows have more than one problem.',
  },
};

function wholePercent(value: string): string {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? String(Math.round(parsed)) : value;
}

function averageLabel(value: string): string {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed.toFixed(1) : value;
}

function formatCount(value: number): string {
  return Number.isFinite(value) ? new Intl.NumberFormat('en-US').format(value) : String(value);
}

function importOutcomeMessage(report: InsightResponse): string {
  const { rowsRead, importedCount, rejectedCount, rejectedPercentage } = report.measured;
  if (rejectedCount <= 0) return 'All rows were imported.';
  return `${formatCount(importedCount)} valid rows were imported. ${formatCount(rejectedCount)} of ${formatCount(rowsRead)} rows need attention (${rejectedPercentage}%).`;
}

function rejectedShareMessage(report: InsightResponse): string {
  const percentage = wholePercent(report.measured.rejectedPercentage);
  return Number.isFinite(Number(percentage))
    ? `${percentage}% of the submitted rows could not be imported.`
    : 'Some submitted rows could not be imported.';
}

export function providerFailureOutcome(failure: InsightFailureResponse): string {
  if (failure.code.endsWith('_SEMANTIC_REJECTED')) return 'response rejected';
  if (failure.providerStatus === 'NO_RESPONSE' || failure.code.endsWith('_NETWORK')) return 'no response';
  return 'request failed';
}

function Tooltip({ label }: { label: string }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <span className="relative inline-flex items-center align-middle">
      <button
        type="button"
        aria-label={label}
        aria-describedby={open ? id : undefined}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onClick={() => setOpen((previous) => !previous)}
        className="ml-1 inline-flex size-4 items-center justify-center rounded-full text-ink-500 hover:text-lake-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-lake-500"
      >
        <Info size={12} aria-hidden="true" />
      </button>
      <span className="sr-only">{label}</span>
      {open && (
        <span
          id={id}
          role="tooltip"
          className="absolute bottom-full right-0 z-10 mb-2 w-52 max-w-[calc(100vw-2rem)] rounded-md bg-ink-950 px-3 py-2 text-xs leading-snug text-white shadow-lg sm:left-1/2 sm:right-auto sm:-translate-x-1/2"
        >
          {label}
        </span>
      )}
    </span>
  );
}

function severityDot(severity: InsightSeverity): string {
  if (severity === 'HIGH') return 'bg-danger';
  if (severity === 'MEDIUM') return 'bg-bonus';
  if (severity === 'LOW') return 'bg-lake-500';
  return 'bg-line';
}

function sourceMessage(report: InsightResponse): string {
  if (report.engine === 'RULE_BASED') {
    return report.fallbackReason && report.fallbackReason !== 'AI_DISABLED'
      ? "AI explanations were unavailable. ClearGrade's built-in analysis is shown instead."
      : 'ClearGrade built-in analysis';
  }
  if (report.fallbackReason && report.fallbackReason.includes('GEMINI') && report.engine === 'OLLAMA') {
    return 'Gemini was unavailable, so Ollama provided the explanation.';
  }
  return report.engine === 'GEMINI'
    ? 'Explanation provided by Gemini'
    : 'Explanation provided by Ollama';
}

function SummaryCards({ report }: { report: InsightResponse }) {
  const imported = wholePercent(report.measured.importedPercentage);
  const rejected = wholePercent(report.measured.rejectedPercentage);
  return (
    <section aria-labelledby="summary-heading" className="mt-6">
      <h4 id="summary-heading" className="text-sm font-semibold text-ink-950">At a glance</h4>
      <div className="mt-3 grid grid-cols-1 gap-3 min-[400px]:grid-cols-3 sm:grid-cols-3">
        <div className="min-w-0 rounded-xl border border-line bg-surface p-3 min-[400px]:p-4">
          <p className="text-xs text-ink-500">
            Rows checked <Tooltip label="The number of data rows ClearGrade read from your file." />
          </p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-ink-950 min-[400px]:text-3xl">{report.measured.rowsRead}</p>
        </div>
        <div className="min-w-0 rounded-xl border border-line bg-surface p-3 min-[400px]:p-4">
          <p className="text-xs text-ink-500">
            Successfully imported <Tooltip label="Rows added to your student records." />
          </p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-lake-700 min-[400px]:text-3xl">
            {report.measured.importedCount}
            <span className="ml-1.5 text-xs font-medium text-ink-500 min-[400px]:ml-2 min-[400px]:text-sm">{imported}%</span>
          </p>
        </div>
        <div className="min-w-0 rounded-xl border border-line bg-surface p-3 min-[400px]:p-4">
          <p className="text-xs text-ink-500">
            Need attention <Tooltip label="Rows that could not be imported because of a problem." />
          </p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-danger min-[400px]:text-3xl">
            {report.measured.rejectedCount}
            <span className="ml-1.5 text-xs font-medium text-ink-500 min-[400px]:ml-2 min-[400px]:text-sm">{rejected}%</span>
          </p>
        </div>
      </div>
      <div className="mt-4 min-w-0">
        <div
          className="flex h-3 w-full max-w-full overflow-hidden rounded-full bg-lake-050"
          role="img"
          aria-label={`${imported}% imported, ${rejected}% need attention`}
        >
          <div className="h-full bg-lake-500" style={{ width: `${imported}%` }} />
          <div className="h-full bg-danger" style={{ width: `${rejected}%` }} />
        </div>
        <div className="mt-2 flex flex-wrap gap-4 text-xs text-ink-700">
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-full bg-lake-500" aria-hidden="true" /> Imported ({imported}%)
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-full bg-danger" aria-hidden="true" /> Needs attention ({rejected}%)
          </span>
        </div>
        <p className="mt-3 text-xs text-ink-500">
          Average issues per rejected row: {averageLabel(report.measured.errorsPerRejectedRow)}
          <Tooltip label={`On average, each rejected row had ${averageLabel(report.measured.errorsPerRejectedRow)} issue(s).`} />
        </p>
      </div>
    </section>
  );
}

function IssueCard({ finding }: { finding: InsightFindingResponse }) {
  const content = ISSUE_CONTENT[finding.evidenceMetric] ?? {
    heading: finding.title,
    description: finding.explanation,
  };
  const parsedCount = Number(finding.evidenceValue);
  const count = Number.isFinite(parsedCount) ? String(parsedCount) : finding.evidenceValue;
  const isAverage = finding.evidenceMetric === 'ERRORS_PER_REJECTED_ROW';
  return (
    <li className="min-w-0 rounded-xl border border-line bg-surface p-4 sm:p-5">
      <div className="flex items-start gap-3">
        <div className="flex min-w-0 flex-1 items-start gap-2">
          <span className={`mt-1.5 size-2.5 shrink-0 rounded-full ${severityDot(finding.severity)}`} aria-hidden="true" />
          <div className="min-w-0">
            <h5 className="text-sm font-semibold text-ink-950">{content.heading}</h5>
            <p className="mt-1 text-xs leading-relaxed text-ink-700">{content.description}</p>
          </div>
        </div>
        <span className="cg-tag shrink-0 whitespace-nowrap bg-lake-050/60 text-xs font-semibold tabular-nums text-ink-950">
          {isAverage ? `${finding.evidenceValue} avg. issues` : `${count} ${count === '1' ? 'case' : 'cases'}`}
        </span>
      </div>
      <p className="mt-3 border-t border-line pt-3 text-xs leading-relaxed text-ink-700">
        <span className="font-semibold">How to fix it:</span> {finding.recommendedAction}
      </p>
    </li>
  );
}

function TechnicalDetails({ report, status, availability }: {
  report: InsightResponse;
  status?: AiStatusResponse;
  availability?: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <details
      className="mt-6 min-w-0 rounded-xl border border-line bg-surface"
      open={open}
      onToggle={(event) => setOpen(event.currentTarget.open)}
    >
      <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-ink-950">
        Technical details
      </summary>
      {open ? <div className="min-w-0 space-y-3 border-t border-line px-4 py-4 text-xs text-ink-700">
        <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <div className="cg-break-anywhere">
            <dt className="font-semibold sm:inline">Final analysis engine:</dt>{' '}
            <dd className="cg-break-anywhere mt-0.5 sm:mt-0 sm:inline">{report.engine}</dd>
          </div>
          <div className="cg-break-anywhere">
            <dt className="font-semibold sm:inline">Model:</dt>{' '}
            <dd className="cg-break-anywhere mt-0.5 sm:mt-0 sm:inline">{report.engineModel || 'not recorded'}</dd>
          </div>
          <div className="cg-break-anywhere">
            <dt className="font-semibold sm:inline">Fallback chain:</dt>{' '}
            <dd className="cg-break-anywhere mt-0.5 sm:mt-0 sm:inline">{report.fallbackReason || 'none'}</dd>
          </div>
          <div className="cg-break-anywhere">
            <dt className="font-semibold sm:inline">Cache:</dt>{' '}
            <dd className="cg-break-anywhere mt-0.5 sm:mt-0 sm:inline">{report.cached ? 'cached' : 'fresh'}</dd>
          </div>
        </dl>
        <p className="cg-break-anywhere">
          <span className="font-semibold">Accepted explanation:</span>{' '}
          {report.engine === 'RULE_BASED'
            ? 'none — deterministic analysis used'
            : report.engine === 'GEMINI' ? 'Gemini' : 'Ollama'}
        </p>
        {status?.routing ? <p className="cg-break-anywhere"><span className="font-semibold">Routing:</span> {status.routing}</p> : null}
        {status?.lastAnalysisFailures?.length ? (
          <div className="min-w-0">
            <p className="font-semibold">Provider request outcomes</p>
            <ul className="mt-1 space-y-1" aria-label="Provider failure diagnostics">
              {status.lastAnalysisFailures.map((failure, index) => (
                <li key={`${failure.engine}-${index}`} className="cg-break-anywhere">
                  {failure.engine}: {failure.code} · {failure.providerStatus || 'status unavailable'} · {failure.durationMs} ms · {providerFailureOutcome(failure)}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
        <div className="min-w-0">
          <p className="font-semibold">Evidence</p>
          <ul className="mt-1 space-y-1 font-mono">
            {report.findings.map((finding, index) => (
              <li key={`${finding.evidenceMetric}-${index}`} className="cg-break-anywhere">
                {finding.evidenceMetric} = {finding.evidenceValue}
              </li>
            ))}
          </ul>
        </div>
        {report.usage.available ? (
          <p className="cg-break-anywhere">
            <span className="font-semibold">Usage:</span> input {report.usage.inputTokens ?? '—'}, output{' '}
            {report.usage.outputTokens ?? '—'}, total {report.usage.totalTokens ?? '—'}, {report.usage.durationMs} ms
          </p>
        ) : (
          <p>Usage metadata was not supplied by the selected engine.</p>
        )}
        {availability}
      </div> : null}
    </details>
  );
}

export function IntelligenceReport({ report, status, availability }: {
  report: InsightResponse;
  status?: AiStatusResponse;
  availability?: ReactNode;
}) {
  const rejectedFinding = report.findings.find((finding) => finding.evidenceMetric === 'REJECTED_PERCENTAGE');
  const issueFindings = report.findings.filter((finding) => finding.evidenceMetric !== 'REJECTED_PERCENTAGE');
  const rejectedRatio = report.measured.rowsRead > 0
    ? report.measured.rejectedCount / report.measured.rowsRead
    : 0;

  return (
    <div className="mt-6 min-w-0 border-t border-line pt-6">
      <SummaryCards report={report} />

      {!rejectedFinding ? (
        <section aria-label="Import outcome" className="mt-6 rounded-xl border border-lake-500/30 bg-lake-050/60 p-4 sm:p-5">
          <p className="text-sm font-semibold text-ink-950">{importOutcomeMessage(report)}</p>
          <p className="mt-1 text-sm text-ink-700">{report.limitations}</p>
        </section>
      ) : null}

      {rejectedFinding ? (
        <section aria-labelledby="attention-heading" className="mt-6 min-w-0 rounded-xl border border-danger/30 bg-danger/5 p-4 sm:p-5">
          <div className="flex flex-col items-start gap-1.5 sm:flex-row sm:items-center sm:gap-3">
            <span className="cg-tag shrink-0 whitespace-nowrap bg-danger/10 text-xs font-semibold text-danger">High priority</span>
            <span className="text-xs leading-snug text-ink-700">Needs attention before publishing results</span>
          </div>
          <h4 id="attention-heading" className="mt-3 text-base font-semibold text-ink-950">
            {report.limitations}
          </h4>
          <p className="mt-2 text-sm text-ink-700">
            {rejectedRatio > 1 / 3
              ? 'That means more than one-third of this file needs correction before those records can be added.'
              : 'Those rows need correction before they can be added.'}
          </p>
          <p className="mt-3 text-xs text-ink-500">{rejectedShareMessage(report)}</p>
        </section>
      ) : null}

      {issueFindings.length > 0 ? (
        <section aria-labelledby="issues-heading" className="mt-6">
          <h4 id="issues-heading" className="text-sm font-semibold text-ink-950">Why rows were not imported</h4>
          <div className="mt-2 flex flex-wrap gap-4 text-xs text-ink-500" aria-label="Severity legend">
            <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-danger" aria-hidden="true" /> High</span>
            <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-bonus" aria-hidden="true" /> Medium</span>
            <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-lake-500" aria-hidden="true" /> Low</span>
          </div>
          <ul className="mt-3 grid grid-cols-1 gap-3 lg:grid-cols-2">
            {issueFindings.map((finding, index) => (
              <IssueCard key={`${finding.evidenceMetric}-${index}`} finding={finding} />
            ))}
          </ul>
        </section>
      ) : null}

      <section aria-label="Analysis source" className="mt-6 rounded-xl bg-lake-050/60 px-4 py-3">
        <p className="text-sm text-ink-700">{sourceMessage(report)}</p>
        <p className="mt-1 text-xs text-ink-500">
          ClearGrade checks the numbers and decides what needs attention. AI only helps explain the results.
        </p>
      </section>

      <TechnicalDetails report={report} status={status} availability={availability} />

      <p className="mt-4 text-xs text-ink-500">
        Advisory only. This report never changes student marks, grades, pass/fail decisions or import results.
      </p>
    </div>
  );
}
