import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RefreshCw, ShieldCheck, Sparkles } from 'lucide-react';
import { aiKeys, getAiStatus, postInsights } from '../../api/ai';
import { ApiError } from '../../api/client';
import type {
  AiStatusResponse,
  InsightFindingResponse,
  InsightResponse,
  InsightSeverity,
  InsightUsage,
} from '../../types/api';

const SEVERITIES: InsightSeverity[] = ['HIGH', 'MEDIUM', 'LOW', 'INFO'];
const ERROR_CODES = [
  'MISSING_REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'DUPLICATE_ID', 'WRONG_FIELD_COUNT',
] as const;
const CODE_LABELS: Record<string, string> = {
  MISSING_REQUIRED: 'Missing required values',
  INVALID_FORMAT: 'Invalid formats',
  OUT_OF_RANGE: 'Out-of-range values',
  DUPLICATE_ID: 'Duplicate identifiers',
  WRONG_FIELD_COUNT: 'Wrong field counts',
};

const ENGINE_LABELS: Record<string, string> = {
  GEMINI: 'Gemini',
  OLLAMA: 'Ollama',
  RULE_BASED: 'Rule-based',
};

function severityClass(severity: InsightSeverity): string {
  switch (severity) {
    case 'HIGH': return 'border-l-danger text-danger';
    case 'MEDIUM': return 'border-l-bonus text-ink-950';
    case 'LOW': return 'border-l-brand-600 text-ink-950';
    default: return 'border-l-line text-ink-700';
  }
}

function friendlyError(error: Error): string {
  if (!(error instanceof ApiError)) return 'The analysis could not be completed. Import results are unchanged.';
  switch (error.code) {
    case 'ANALYSIS_COOLDOWN':
      return 'An analysis was requested very recently. Wait a moment, then run a new analysis.';
    case 'AI_CONFIGURATION_ERROR':
      return 'A configured AI provider rejected the request. Check the backend provider configuration.';
    case 'ANALYSIS_PROVIDER_FAILED':
      return 'The configured AI providers could not produce a usable result. No safe fallback was available.';
    case 'BATCH_NOT_COMPLETED':
      return 'Analysis is available only after the import has completed.';
    case 'NETWORK_ERROR':
      return error.message;
    default:
      return 'The analysis could not be completed. Import results are unchanged.';
  }
}

function ProviderAvailability({ status, loading, unavailable }: {
  status: AiStatusResponse | undefined;
  loading: boolean;
  unavailable: boolean;
}) {
  return (
    <div className="mt-5 rounded-lg border border-line bg-canvas px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-wider text-ink-500">Provider configuration</p>
      {unavailable && (
        <p className="mt-2 text-xs text-ink-700">
          Provider status could not be checked. Rule-based analysis remains available offline.
        </p>
      )}
      <ul className="mt-2 flex flex-wrap gap-x-6 gap-y-2 text-xs" aria-label="Provider configuration">
        <li className="text-ink-700">
          Gemini: <span className="font-semibold">{loading ? 'Checking…' : status?.geminiConfigured ? 'Configured' : 'Not configured'}</span>
          {status?.geminiConfigured && status.geminiModel ? <span className="text-ink-500"> · {status.geminiModel}</span> : null}
        </li>
        <li className="text-ink-700">
          Ollama service: <span className="font-semibold">{loading ? 'Checking…' : status?.ollamaReachable ? 'Reachable' : 'Unreachable'}</span>
        </li>
        <li className="text-ink-700">
          Ollama model: <span className="font-semibold">{loading ? 'Checking…' : status?.ollamaModelPresent ? 'Present' : 'Missing'}</span>
          {status?.ollamaModelPresent && status.ollamaModel ? <span className="text-ink-500"> · {status.ollamaModel}</span> : null}
        </li>
      </ul>
      <p className="mt-2 text-xs text-ink-500">
        Inference: not tested until an analysis request runs. Configuration and reachability do not prove generation will succeed.
      </p>
      <p className="mt-1 text-xs text-ink-700">
        Last analysis: <span className="font-semibold">{status?.lastAnalysisEngine ?? 'None yet'}</span>
        {status?.lastAnalysisReason ? <span className="text-ink-500"> · {status.lastAnalysisReason}</span> : null}
      </p>
      {status && <p className="mt-2 text-xs text-ink-500">Configured routing: {status.routing}</p>}
    </div>
  );
}

function UsagePanel({ usage }: { usage: InsightUsage }) {
  if (!usage?.available) {
    return <p className="mt-3 text-xs text-ink-500">Usage unavailable — the provider returned no safe usage metadata.</p>;
  }
  return (
    <section aria-labelledby="usage-heading" className="mt-5 rounded-lg border border-line bg-canvas px-4 py-3">
      <h4 id="usage-heading" className="text-xs font-semibold uppercase tracking-wider text-ink-500">Token usage</h4>
      <dl className="mt-2 flex flex-wrap gap-x-6 gap-y-2 text-xs text-ink-700">
        <div><dt className="inline">Input: </dt><dd className="inline font-semibold tabular-nums">{usage.inputTokens ?? '—'}</dd></div>
        <div><dt className="inline">Output: </dt><dd className="inline font-semibold tabular-nums">{usage.outputTokens ?? '—'}</dd></div>
        <div><dt className="inline">Thinking: </dt><dd className="inline font-semibold tabular-nums">{usage.thinkingTokens ?? '—'}</dd></div>
        <div><dt className="inline">Total: </dt><dd className="inline font-semibold tabular-nums">{usage.totalTokens ?? '—'}</dd></div>
        <div><dt className="inline">Model: </dt><dd className="inline font-semibold">{usage.modelId || '—'}</dd></div>
        <div><dt className="inline">Provider status: </dt><dd className="inline font-semibold">{usage.providerStatus || '—'}</dd></div>
        <div><dt className="inline">Interaction: </dt><dd className="inline font-semibold">{usage.interactionStatus || '—'}</dd></div>
        <div><dt className="inline">Duration: </dt><dd className="inline font-semibold tabular-nums">{usage.durationMs} ms</dd></div>
      </dl>
    </section>
  );
}

function MeasuredFacts({ measured }: { measured: InsightResponse['measured'] }) {
  return (
    <section aria-labelledby="measured-heading">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h4 id="measured-heading" className="text-sm font-semibold">Measured import quality</h4>
        <span className="cg-tag bg-canvas">Measured by ClearGrade · never AI-generated</span>
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-lg border border-line p-3">
          <dt className="text-xs text-ink-500">Rows read</dt>
          <dd className="cg-stat-value text-xl">{measured.rowsRead}</dd>
        </div>
        <div className="rounded-lg border border-line p-3">
          <dt className="text-xs text-ink-500">Imported</dt>
          <dd className="cg-stat-value text-xl">{measured.importedPercentage}%</dd>
        </div>
        <div className="rounded-lg border border-line p-3">
          <dt className="text-xs text-ink-500">Rejected</dt>
          <dd className="cg-stat-value text-xl">{measured.rejectedPercentage}%</dd>
        </div>
        <div className="rounded-lg border border-line p-3">
          <dt className="text-xs text-ink-500">Problems / rejected row</dt>
          <dd className="cg-stat-value text-xl">{measured.errorsPerRejectedRow}</dd>
        </div>
      </dl>
      <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-2 text-xs text-ink-700 sm:grid-cols-3">
        <div className="flex justify-between gap-2"><dt>New</dt><dd className="tabular-nums">{measured.newCount}</dd></div>
        <div className="flex justify-between gap-2"><dt>Changed</dt><dd className="tabular-nums">{measured.changedCount}</dd></div>
        <div className="flex justify-between gap-2"><dt>Unchanged</dt><dd className="tabular-nums">{measured.unchangedCount}</dd></div>
        <div className="flex justify-between gap-2"><dt>Error occurrences</dt><dd className="tabular-nums">{measured.errorCount}</dd></div>
        {ERROR_CODES.map((code) => (
          <div key={code} className="flex justify-between gap-2">
            <dt>{CODE_LABELS[code]}</dt>
            <dd className="tabular-nums">{measured.errorCountsByCode[code] ?? 0}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function Finding({ finding }: { finding: InsightFindingResponse }) {
  return (
    <li className={`rounded-lg border border-line border-l-4 bg-surface p-4 ${severityClass(finding.severity)}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h5 className="text-sm font-semibold text-ink-950">{finding.title}</h5>
        <span className="cg-tag bg-canvas text-xs font-semibold" aria-label={`Severity ${finding.severity}`}>
          {finding.severity}
        </span>
      </div>
      <dl className="mt-3 rounded-md bg-canvas px-3 py-2">
        <div className="flex flex-wrap gap-x-2 text-xs">
          <dt className="font-semibold text-ink-500">Evidence metric:</dt>
          <dd className="font-mono text-ink-700">{finding.evidenceMetric}</dd>
        </div>
        <div className="flex flex-wrap gap-x-2 text-xs">
          <dt className="font-semibold text-ink-500">Exact value:</dt>
          <dd className="font-mono tabular-nums text-ink-950">{finding.evidenceValue}</dd>
        </div>
      </dl>
      <p className="mt-3 text-sm text-ink-700">{finding.explanation}</p>
      <p className="mt-2 text-sm text-ink-700"><span className="font-semibold">Recommended:</span> {finding.recommendedAction}</p>
    </li>
  );
}

function Report({ report }: { report: InsightResponse }) {
  const groups = SEVERITIES.map((severity) => ({
    severity,
    findings: report.findings.filter((finding) => finding.severity === severity),
  })).filter((group) => group.findings.length > 0);

  return (
    <div className="mt-6 space-y-6 border-t border-line pt-6">
      <div className="flex flex-wrap items-center gap-2" aria-label="Analysis source">
        <span className="cg-tag bg-brand-100 font-semibold text-brand-700">
          Generated by {ENGINE_LABELS[report.engine] ?? report.engine}
        </span>
        {report.engineModel && <span className="text-xs text-ink-500">{report.engineModel}</span>}
        <span className="cg-tag bg-canvas" aria-label="Cache status">
          {report.cached ? 'Cached report · no new provider request' : 'Fresh analysis · one provider request'}
        </span>
      </div>

      {report.engine === 'RULE_BASED' && (
        <p className="rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink-700" role="status">
          Rule-based analysis was used. Fallback reason: {report.fallbackReason ?? 'not reported'}.
        </p>
      )}
      {report.fallbackReason && report.engine !== 'RULE_BASED' && (
        <p className="text-xs text-ink-500">Fallback reason: {report.fallbackReason}</p>
      )}

      <MeasuredFacts measured={report.measured} />

      <section aria-labelledby="interpretation-heading">
        <h4 id="interpretation-heading" className="text-sm font-semibold">Interpretation</h4>
        <p className="mt-2 text-sm text-ink-700">{report.summary}</p>
        <p className="mt-2 text-sm text-ink-700">{report.overallAssessment}</p>
        <p className="mt-2 text-xs text-ink-500">Written by the selected engine from measured aggregates only.</p>
      </section>

      {groups.length > 0 && (
        <section aria-labelledby="findings-heading">
          <h4 id="findings-heading" className="text-sm font-semibold">Findings by severity</h4>
          <div className="mt-3 space-y-5">
            {groups.map((group) => (
              <div key={group.severity}>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-ink-500">{group.severity}</p>
                <ul className="space-y-3">
                  {group.findings.map((finding, index) => (
                    <Finding key={`${finding.evidenceMetric}-${index}`} finding={finding} />
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>
      )}

      {report.recommendedActions.length > 0 && (
        <section aria-labelledby="actions-heading">
          <h4 id="actions-heading" className="text-sm font-semibold">Recommended actions</h4>
          <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-ink-700">
            {report.recommendedActions.map((action, index) => <li key={index}>{action}</li>)}
          </ol>
        </section>
      )}

      <section aria-labelledby="limits-heading">
        <h4 id="limits-heading" className="text-sm font-semibold">Limitations</h4>
        <p className="mt-2 text-sm text-ink-700">{report.limitations}</p>
      </section>

      <UsagePanel usage={report.usage} />

      <p className="rounded-lg border border-line bg-canvas px-4 py-3 text-xs text-ink-700">
        Advisory only. Findings and recommendations never alter student marks, grades, pass/fail decisions,
        accepted or rejected rows, or import counters.
      </p>
    </div>
  );
}

export function IntelligencePanel({ batchId, disabled = false }: { batchId: number; disabled?: boolean }) {
  const [triggered, setTriggered] = useState(false);
  const [refresh, setRefresh] = useState(false);
  const [run, setRun] = useState(0);

  const status = useQuery({
    queryKey: aiKeys.status(),
    queryFn: ({ signal }) => getAiStatus(signal),
    staleTime: 60_000,
    retry: false,
  });

  const insight = useQuery({
    queryKey: aiKeys.insights(batchId, refresh, run),
    queryFn: ({ signal }) => postInsights(batchId, refresh, signal),
    enabled: triggered && !disabled,
    retry: false,
    staleTime: 0,
  });

  useEffect(() => {
    setTriggered(false);
    setRefresh(false);
    setRun(0);
  }, [batchId]);

  function analyse(forceNew: boolean) {
    setTriggered(true);
    if (forceNew) setRefresh(true);
    setRun((previous) => previous + 1);
  }

  return (
    <section aria-labelledby="intelligence-heading" className="cg-card mt-10 p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-brand-700">
            <Sparkles size={14} aria-hidden="true" /> Signature feature
          </p>
          <h3 id="intelligence-heading" className="mt-2 text-xl font-semibold">ClearGrade Intelligence</h3>
          <p className="mt-2 max-w-2xl text-sm text-ink-700">
            Interpret this import's measured data-quality counters. Analysis is advisory and never changes student results.
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <button
            type="button"
            onClick={() => analyse(Boolean(insight.data))}
            disabled={disabled || insight.isFetching}
            className="cg-btn-primary"
            aria-describedby="privacy-disclosure"
          >
            {insight.isFetching ? 'Analysing…' : insight.data ? 'Run new analysis' : 'Analyse import'}
          </button>
          {insight.data && (
            <span className="text-xs text-ink-500">Runs another provider request and may consume quota.</span>
          )}
        </div>
      </div>

      <p id="privacy-disclosure" className="mt-4 flex items-start gap-2 rounded-lg border border-line bg-canvas px-4 py-3 text-xs text-ink-700">
        <ShieldCheck size={16} className="mt-0.5 shrink-0 text-brand-700" aria-hidden="true" />
        Aggregate-only privacy: providers receive counts, backend-calculated percentages and static schema metadata only —
        never student names, IDs, emails, dates of birth, individual marks, raw rows, row messages or physical line numbers.
      </p>

      <ProviderAvailability
        status={status.data}
        loading={status.isPending}
        unavailable={status.isError}
      />

      <div aria-live="polite" className="mt-5">
        {disabled && (
          <p className="text-sm text-ink-500">Analysis becomes available when this import has completed.</p>
        )}
        {!disabled && !triggered && (
          <p className="text-sm text-ink-500">
            No analysis has been requested. Import results stay visible whether or not you run one.
          </p>
        )}
        {triggered && insight.isPending && (
          <p className="flex items-center gap-2 text-sm text-ink-700" role="status">
            <RefreshCw size={16} className="animate-spin" aria-hidden="true" /> Analysing aggregate import metrics…
          </p>
        )}
        {triggered && insight.isError && (
          <div className="rounded-lg border border-line bg-surface p-4" role="alert">
            <p className="text-sm font-semibold text-ink-950">Analysis could not be completed</p>
            <p className="mt-2 text-sm text-ink-700">{friendlyError(insight.error as Error)}</p>
            <button type="button" onClick={() => analyse(Boolean(insight.data))} className="cg-btn-secondary mt-4">
              <RefreshCw size={14} aria-hidden="true" /> Retry analysis
            </button>
          </div>
        )}
        {insight.data && <Report report={insight.data} />}
      </div>
    </section>
  );
}
