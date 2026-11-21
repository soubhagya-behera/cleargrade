import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, ShieldCheck, Sparkles } from 'lucide-react';
import { aiKeys, getAiStatus, postInsights } from '../../api/ai';
import { ApiError } from '../../api/client';
import { IntelligenceReport, providerFailureOutcome } from '../../components/ui/IntelligenceReport';
import type { AiStatusResponse } from '../../types/api';

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

function inferenceMessage(status: AiStatusResponse | undefined, loading: boolean, unavailable: boolean): string {
  if (unavailable) return 'Provider status could not be checked. Rule-based analysis remains available offline.';
  if (loading || !status) return 'Checking provider status…';
  if (!status.lastAnalysisEngine && !status.lastAnalysisReason) {
    return 'No analysis request has run yet. Configuration and reachability do not prove generation will succeed.';
  }
  if (!status.lastAnalysisEngine) {
    return 'The last analysis request did not complete with an accepted explanation.';
  }
  if (status.lastAnalysisEngine === 'RULE_BASED') {
    return status.lastAnalysisFailures?.length
      ? 'Provider requests were attempted, but no AI response was accepted. ClearGrade used its deterministic analysis.'
      : 'No AI response was used. ClearGrade used its deterministic analysis.';
  }
  return `An accepted explanation was produced by ${status.lastAnalysisEngine}.`;
}

function lastAnalysisLabel(status: AiStatusResponse | undefined, loading: boolean, unavailable: boolean): string {
  if (unavailable) return 'Unavailable';
  if (loading || !status) return 'Checking…';
  if (status.lastAnalysisEngine) return status.lastAnalysisEngine;
  return status.lastAnalysisReason ? 'No engine completed' : 'None yet';
}

function ProviderAvailability({ status, loading, unavailable }: {
  status: AiStatusResponse | undefined;
  loading: boolean;
  unavailable: boolean;
}) {
  return (
    <div className="mt-5 min-w-0 rounded-lg border border-line bg-lake-050/60 px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-wider text-ink-500">Provider configuration</p>
      <ul className="mt-2 flex flex-wrap gap-x-6 gap-y-2 text-xs" aria-label="Provider configuration">
        <li className="cg-break-anywhere min-w-0 text-ink-700">
          Gemini: <span className="font-semibold">{loading ? 'Checking…' : status?.geminiConfigured ? 'Configured' : 'Not configured'}</span>
          {status?.geminiConfigured && status.geminiModel ? <span className="text-ink-500"> · {status.geminiModel}</span> : null}
        </li>
        <li className="cg-break-anywhere min-w-0 text-ink-700">
          Ollama service: <span className="font-semibold">{loading ? 'Checking…' : status?.ollamaReachable ? 'Reachable' : 'Unreachable'}</span>
        </li>
        <li className="cg-break-anywhere min-w-0 text-ink-700">
          Ollama model: <span className="font-semibold">{loading ? 'Checking…' : status?.ollamaModelPresent ? 'Present' : 'Missing'}</span>
          {status?.ollamaModelPresent && status.ollamaModel ? <span className="text-ink-500"> · {status.ollamaModel}</span> : null}
        </li>
      </ul>
      <p className="cg-break-anywhere mt-2 text-xs text-ink-500">{inferenceMessage(status, loading, unavailable)}</p>
      <div className="mt-2 min-w-0">
        <p className="text-xs text-ink-700">
          Last analysis: <span className="font-semibold">{lastAnalysisLabel(status, loading, unavailable)}</span>
        </p>
        {status?.lastAnalysisReason ? (
          <p className="cg-break-anywhere mt-1 text-xs text-ink-500">Last analysis reason: {status.lastAnalysisReason}</p>
        ) : null}
      </div>
      {status?.lastAnalysisFailures?.length ? (
        <div className="mt-2 min-w-0">
          <p className="text-xs font-semibold text-ink-700">Provider request outcomes</p>
          <ul className="mt-1 space-y-1 text-xs text-ink-700" aria-label="Provider failure diagnostics">
            {status.lastAnalysisFailures.map((failure, index) => (
              <li key={`${failure.engine}-${index}`} className="cg-break-anywhere">
                {failure.engine}: {failure.code} · {failure.providerStatus || 'status unavailable'} ·
                {' '}{failure.durationMs} ms · {providerFailureOutcome(failure)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {status && <p className="cg-break-anywhere mt-2 text-xs text-ink-500"><span className="contents">Config</span>ured routing: {status.routing}</p>}
    </div>
  );
}

export function IntelligencePanel({ batchId, disabled = false }: { batchId: number; disabled?: boolean }) {
  const [triggered, setTriggered] = useState(false);
  const [refresh, setRefresh] = useState(false);
  const [run, setRun] = useState(0);
  const queryClient = useQueryClient();

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

  useEffect(() => {
    if (!insight.isSuccess || !insight.data) return;
    void queryClient.invalidateQueries({ queryKey: aiKeys.status() });
  }, [insight.isSuccess, insight.dataUpdatedAt, queryClient]);

  function analyse(forceNew: boolean) {
    setTriggered(true);
    if (forceNew) setRefresh(true);
    setRun((previous) => previous + 1);
  }

  const availability = (
    <ProviderAvailability status={status.data} loading={status.isPending || status.isFetching} unavailable={status.isError} />
  );

  return (
    <section aria-labelledby="intelligence-heading" className="cg-card cg-rise-3 min-w-0 p-5 sm:p-7">
      <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-lake-700">
            <Sparkles size={14} aria-hidden="true" /> ClearGrade Intelligence
          </p>
          <h3 id="intelligence-heading" className="mt-2 text-xl font-semibold">Import quality report</h3>
          <p className="mt-2 max-w-2xl text-sm text-ink-700">
            Here's what happened when ClearGrade checked your file.
          </p>
        </div>
        <div className="flex min-w-0 flex-col items-stretch gap-1 text-left sm:items-end sm:text-right">
          <button
            type="button"
            onClick={() => analyse(Boolean(insight.data))}
            disabled={disabled || insight.isFetching}
            className="cg-btn-accent"
            aria-describedby="privacy-disclosure"
          >
            {insight.isFetching ? 'Analysing…' : insight.data ? 'Run new analysis' : 'Analyse import'}
          </button>
          {insight.data && (
            <span className="cg-break-anywhere max-w-full text-xs text-ink-500">Runs another provider request and may consume quota.</span>
          )}
        </div>
      </div>

      <p id="privacy-disclosure" className="mt-4 flex min-w-0 items-start gap-2 rounded-lg border border-line bg-lake-050/60 px-4 py-3 text-xs text-ink-700">
        <ShieldCheck size={16} className="mt-0.5 shrink-0 text-lake-700" aria-hidden="true" />
        <span className="cg-break-anywhere min-w-0">Only totals and issue counts are shared with AI. No individual student records are sent.</span>
      </p>

      <div aria-live="polite" className="mt-5">
        {disabled && (
          <p className="text-sm text-ink-500">Analysis becomes available when this import has completed.</p>
        )}
        {!disabled && !triggered && (
          <p className="text-sm text-ink-500">
            No analysis has been requested. Your import results stay visible.
          </p>
        )}
        {triggered && insight.isPending && (
          <p className="flex items-center gap-2 text-sm text-ink-700" role="status">
            <RefreshCw size={16} className="animate-spin" aria-hidden="true" /> Checking your import…
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
        {insight.data && (
          <IntelligenceReport report={insight.data} status={status.data} availability={availability} />
        )}
      </div>
    </section>
  );
}
