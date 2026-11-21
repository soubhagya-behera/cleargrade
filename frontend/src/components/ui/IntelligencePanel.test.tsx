import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { ApiError } from '../../api/client';
import { IntelligencePanel } from '../../components/ui/IntelligencePanel';
import { renderWithProviders } from '../../test/render';
import type { AiStatusResponse, InsightResponse, InsightUsage } from '../../types/api';

vi.mock('../../api/ai', () => ({
  aiKeys: {
    status: () => ['ai', 'status'],
    insights: (id: number, refresh = false, run = 0) => ['ai', 'insights', id, refresh, run],
  },
  getAiStatus: vi.fn(),
  postInsights: vi.fn(),
}));

const { getAiStatus, postInsights } = await import('../../api/ai');

const usage: InsightUsage = {
  available: true,
  inputTokens: 120,
  outputTokens: 300,
  thinkingTokens: 50,
  totalTokens: 470,
  modelId: 'llama3.2:1b',
  providerStatus: 'HTTP_200',
  interactionStatus: 'completed',
  durationMs: 1234,
};

const status: AiStatusResponse = {
  enabled: true,
  geminiConfigured: true,
  geminiModel: 'gemini-3.8-flash',
  ollamaConfigured: true,
  ollamaReachable: true,
  ollamaModelPresent: true,
  ollamaModel: 'llama3.2:1b',
  inferenceTested: false,
  deterministicAvailable: true,
  defaultEngine: 'GEMINI',
  routing: 'GEMINI_THEN_OLLAMA_THEN_RULE_BASED',
  routingReasons: [],
  lastAnalysisEngine: 'OLLAMA',
  lastAnalysisReason: 'GEMINI:GEMINI_SERVER',
  lastAnalysisUsage: { available: false, inputTokens: null, outputTokens: null, thinkingTokens: null,
    totalTokens: null, modelId: '', providerStatus: '', interactionStatus: '', durationMs: 0 },
  lastAnalysisFailures: [{
    engine: 'GEMINI', code: 'GEMINI_SERVER', providerStatus: 'HTTP_503',
    durationMs: 321, fallbackSucceeded: true,
  }],
};

const initialStatus: AiStatusResponse = {
  ...status,
  lastAnalysisEngine: null,
  lastAnalysisReason: null,
  lastAnalysisFailures: [],
};

const deterministicStatus: AiStatusResponse = {
  ...status,
  lastAnalysisEngine: 'RULE_BASED',
  lastAnalysisReason: 'GEMINI:GEMINI_NETWORK;OLLAMA:OLLAMA_SEMANTIC_REJECTED',
  lastAnalysisUsage: { ...status.lastAnalysisUsage, modelId: 'deterministic-v1', providerStatus: 'NOT_RECORDED' },
  lastAnalysisFailures: [
    { engine: 'GEMINI', code: 'GEMINI_NETWORK', providerStatus: 'NO_RESPONSE', durationMs: 60017, fallbackSucceeded: true },
    { engine: 'OLLAMA', code: 'OLLAMA_SEMANTIC_REJECTED', providerStatus: 'HTTP_200', durationMs: 4164, fallbackSucceeded: true },
  ],
};

function report(overrides: Partial<InsightResponse> = {}): InsightResponse {
  return {
    batchId: 7,
    engine: 'OLLAMA',
    engineModel: 'llama3.2:1b',
    fallbackReason: 'GEMINI:GEMINI_SERVER',
    cached: false,
    measured: {
      rowsRead: 25, importedCount: 16, rejectedCount: 9,
      newCount: 16, changedCount: 0, unchangedCount: 0, errorCount: 9,
      importedPercentage: '64.0', rejectedPercentage: '36.0', errorsPerRejectedRow: '1.00',
      errorCountsByCode: {
        MISSING_REQUIRED: 2, INVALID_FORMAT: 3, OUT_OF_RANGE: 2,
        DUPLICATE_ID: 1, WRONG_FIELD_COUNT: 1,
      },
      schemaColumns: ['student_id'],
    },
    summary: 'Read 25 rows; imported 16 (64.0%) and rejected 9 (36.0%).',
    overallAssessment: 'Rule-based checks detected 6 quality concerns.',
    findings: [
      { title: 'Elevated rejected-row share', severity: 'HIGH', evidenceMetric: 'REJECTED_PERCENTAGE',
        evidenceValue: '36.0', explanation: '9 of 25 rows were rejected (36.0%).',
        recommendedAction: 'Review the rejected rows by error code and correct the source export.' },
      { title: 'Duplicate student identifiers', severity: 'MEDIUM', evidenceMetric: 'DUPLICATE_ID_COUNT',
        evidenceValue: '1', explanation: 'A student ID appears more than once.',
        recommendedAction: 'De-duplicate student IDs before the next upload.' },
      { title: 'Missing required values', severity: 'MEDIUM', evidenceMetric: 'MISSING_REQUIRED_COUNT',
        evidenceValue: '2', explanation: 'Two required values were left blank.',
        recommendedAction: 'Populate all required columns in the source export.' },
      { title: 'Rows with wrong field count', severity: 'MEDIUM', evidenceMetric: 'WRONG_FIELD_COUNT_COUNT',
        evidenceValue: '1', explanation: 'One row has the wrong number of columns.',
        recommendedAction: 'Check delimiters, quoting and trailing columns in the source export.' },
      { title: 'Invalid value formats', severity: 'LOW', evidenceMetric: 'INVALID_FORMAT_COUNT',
        evidenceValue: '3', explanation: 'Three values do not match the required format.',
        recommendedAction: 'Normalise dates, identifiers and email formatting before export.' },
      { title: 'Out-of-range values', severity: 'LOW', evidenceMetric: 'OUT_OF_RANGE_COUNT',
        evidenceValue: '2', explanation: 'Two values fall outside the permitted range.',
        recommendedAction: 'Verify marks and age bounds in the source system.' },
    ],
    recommendedActions: ['Review the rejected rows by error code and correct the source export.'],
    limitations: '9 of 25 rows could not be imported (36%).',
    usage,
    advisoryOnly: true,
    ...overrides,
  };
}

beforeEach(() => {
  vi.mocked(getAiStatus).mockReset();
  vi.mocked(postInsights).mockReset();
  vi.mocked(getAiStatus).mockResolvedValue(status);
  vi.mocked(postInsights).mockResolvedValue(report());
});

describe('IntelligencePanel', () => {
  it('starts with a simple introduction and does not request inference', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} />);

    expect(screen.getByRole('heading', { name: 'Import quality report' })).toBeInTheDocument();
    expect(screen.getByText(/Here's what happened when ClearGrade checked your file/)).toBeInTheDocument();
    expect(screen.getByText(/Only totals and issue counts are shared with AI/)).toBeInTheDocument();
    expect(screen.getByText(/No analysis has been requested/)).toBeInTheDocument();
    expect(postInsights).not.toHaveBeenCalled();
  });

  it('presents a plain-language summary without raw identifiers or developer text', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('9 of 25 rows could not be imported (36%).')).toBeInTheDocument();
    expect(screen.getByText(/That means more than one-third of this file/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /64% imported, 36% need attention/ })).toBeInTheDocument();
    expect(screen.getByText(/Average issues per rejected row: 1\.0/)).toBeInTheDocument();
    expect(screen.getByText(/Gemini was unavailable, so Ollama provided the explanation/)).toBeInTheDocument();
    expect(screen.getByText(/ClearGrade checks the numbers and decides what needs attention/)).toBeInTheDocument();
    expect(screen.getAllByText(/How to fix it:/)).toHaveLength(5);
    expect(screen.queryByText(/REJECTED_PERCENTAGE/)).not.toBeInTheDocument();
    expect(screen.queryByText(/GEMINI_SERVER/)).not.toBeInTheDocument();
    expect(screen.queryByText(/javaExplanation/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"""/)).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Recommended actions' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Limitations' })).not.toBeInTheDocument();
  });

  it('keeps internal evidence out of the normal report and available in technical details', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({
      findings: report().findings.map((finding) => finding.evidenceMetric === 'REJECTED_PERCENTAGE'
        ? { ...finding, explanation: 'A substantial share of the file was rejected. Measured rejected_percentage: 36.0.' }
        : finding),
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('36% of the submitted rows could not be imported.')).toBeInTheDocument();
    expect(screen.queryByText(/Measured rejected_percentage/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Technical details'));
    expect(await screen.findByText(/REJECTED_PERCENTAGE = 36\.0/)).toBeInTheDocument();
  });

  it('refreshes provider status after a deterministic fallback analysis', async () => {
    vi.mocked(getAiStatus).mockReset();
    vi.mocked(getAiStatus).mockResolvedValueOnce(initialStatus).mockResolvedValue(deterministicStatus);
    vi.mocked(postInsights).mockResolvedValue(report({
      engine: 'RULE_BASED',
      engineModel: 'deterministic-v1',
      fallbackReason: 'GEMINI:GEMINI_NETWORK;OLLAMA:OLLAMA_SEMANTIC_REJECTED',
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText(/AI explanations were unavailable/)).toBeInTheDocument();
    await waitFor(() => expect(getAiStatus).toHaveBeenCalledTimes(2));
    fireEvent.click(screen.getByText('Technical details'));

    expect(await screen.findByText(/no AI response was accepted/i)).toBeInTheDocument();
    expect(screen.getByText(/Final analysis engine:/)).toBeInTheDocument();
    expect(screen.getAllByText(/RULE_BASED/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/None yet/)).not.toBeInTheDocument();
    expect(screen.getAllByText(/GEMINI_NETWORK/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/OLLAMA_SEMANTIC_REJECTED/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/no response/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/response rejected/).length).toBeGreaterThan(0);
    expect(screen.getByText(/none — deterministic analysis used/)).toBeInTheDocument();
  });

  it('shows category descriptions and counts in compact issue cards', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('Invalid formats')).toBeInTheDocument();
    expect(screen.getByText(/dates or email addresses/)).toBeInTheDocument();
    expect(screen.getByText('3 cases')).toBeInTheDocument();
    expect(screen.getByText('Duplicate student ID')).toBeInTheDocument();
    expect(screen.getAllByText('1 case')).toHaveLength(2);
    expect(screen.getByText(/High priority/)).toBeInTheDocument();
    expect(screen.getByLabelText('Severity legend')).toBeInTheDocument();
  });

  it('keeps technical diagnostics collapsed until requested', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));
    await screen.findByText('Technical details');

    expect(screen.queryByText(/GEMINI:GEMINI_SERVER/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Technical details'));

    expect(await screen.findByText('GEMINI:GEMINI_SERVER')).toBeInTheDocument();
    expect(screen.getByText(/REJECTED_PERCENTAGE = 36\.0/)).toBeInTheDocument();
    expect(screen.getAllByText(/GEMINI_SERVER/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/HTTP_503/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/Fresh analysis/)).not.toBeInTheDocument();
    expect(screen.getByText(/fresh/)).toBeInTheDocument();
  });

  it('exposes tooltip explanations to hover, focus and assistive technology', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));
    await screen.findByText('Technical details');

    const tip = screen.getByRole('button', { name: /number of data rows ClearGrade read/ });
    fireEvent.focus(tip);
    expect(screen.getByRole('tooltip')).toHaveTextContent(/number of data rows/);
    fireEvent.blur(tip);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    expect(screen.getAllByText(/number of data rows ClearGrade read/).length).toBeGreaterThan(0);
  });

  it('uses ClearGrade wording when the deterministic fallback is used', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({
      engine: 'RULE_BASED',
      engineModel: 'deterministic-v1',
      fallbackReason: 'GEMINI:GEMINI_SERVER;OLLAMA:OLLAMA_SEMANTIC_REJECTED',
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText(/AI explanations were unavailable/)).toBeInTheDocument();
    expect(screen.getByText(/ClearGrade's built-in analysis is shown instead/)).toBeInTheDocument();
    expect(screen.queryByText(/GEMINI_SERVER/)).not.toBeInTheDocument();
  });

  it('attributes a direct Gemini report without fallback language', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({
      engine: 'GEMINI', engineModel: 'gemini-3.8-flash', fallbackReason: null,
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('Explanation provided by Gemini')).toBeInTheDocument();
    expect(screen.queryByText(/Gemini was unavailable/)).not.toBeInTheDocument();
  });

  it('handles a clean import without inventing a high-priority warning', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({
      measured: {
        ...report().measured, rowsRead: 25, importedCount: 25, rejectedCount: 0,
        errorCount: 0, importedPercentage: '100.0', rejectedPercentage: '0.0',
        errorsPerRejectedRow: '0.00',
        errorCountsByCode: {
          MISSING_REQUIRED: 0, INVALID_FORMAT: 0, OUT_OF_RANGE: 0,
          DUPLICATE_ID: 0, WRONG_FIELD_COUNT: 0,
        },
      },
      findings: [],
      limitations: '0 of 25 rows could not be imported (0%).',
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('0 of 25 rows could not be imported (0%).')).toBeInTheDocument();
    expect(screen.queryByText(/High priority/)).not.toBeInTheDocument();
    expect(screen.queryByText('Why rows were not imported')).not.toBeInTheDocument();
  });

  it('states imported and rejected counts when no high-priority finding is present', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({
      measured: {
        ...report().measured,
        rowsRead: 5000, importedCount: 4685, rejectedCount: 315,
        importedPercentage: '93.7', rejectedPercentage: '6.3',
        errorCount: 346, errorsPerRejectedRow: '1.10',
        errorCountsByCode: {
          MISSING_REQUIRED: 100, INVALID_FORMAT: 181, OUT_OF_RANGE: 65,
          DUPLICATE_ID: 0, WRONG_FIELD_COUNT: 0,
        },
      },
      findings: report().findings.filter((finding) => finding.evidenceMetric !== 'REJECTED_PERCENTAGE'),
      limitations: '315 of 5,000 rows could not be imported (6.3%).',
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('4,685 valid rows were imported. 315 of 5,000 rows need attention (6.3%).')).toBeInTheDocument();
    expect(screen.queryByText('All rows were imported.')).not.toBeInTheDocument();
    expect(screen.queryByText(/High priority/)).not.toBeInTheDocument();
  });

  it('labels the multiple-issues evidence as an average instead of a case count', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({
      findings: [{
        title: 'Multiple problems per rejected row', severity: 'MEDIUM',
        evidenceMetric: 'ERRORS_PER_REJECTED_ROW', evidenceValue: '1.10',
        explanation: 'Some rejected rows carry more than one problem.',
        recommendedAction: 'Fix field-level problems together to recover rows in one pass.',
      }],
    }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByText('1.10 avg. issues')).toBeInTheDocument();
    expect(screen.queryByText('1.1 cases')).not.toBeInTheDocument();
    expect(screen.getByText('Multiple problems per rejected row')).toBeInTheDocument();
  });

  it('shows cache state only inside technical details and uses responsive grids', async () => {
    vi.mocked(postInsights).mockResolvedValue(report({ cached: true }));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));
    await screen.findByText('Technical details');

    expect(screen.queryByText(/Cached report/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Technical details'));
    expect(await screen.findByText('cached')).toBeInTheDocument();

    const summaryGrid = screen.getByText('Rows checked').closest('.grid');
    expect(summaryGrid?.className).toContain('sm:grid-cols-3');
  });

  it('uses a fresh analysis only on the first explicit request', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));
    expect(await screen.findByText('9 of 25 rows could not be imported (36%).')).toBeInTheDocument();
    expect(vi.mocked(postInsights).mock.calls[0][1]).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Run new analysis' }));
    await waitFor(() => expect(postInsights).toHaveBeenCalledTimes(2));
    expect(vi.mocked(postInsights).mock.calls[1][1]).toBe(true);
  });

  it('keeps import results visible during loading and retry', async () => {
    vi.mocked(postInsights).mockRejectedValueOnce(new ApiError(429, 'ANALYSIS_COOLDOWN', 'slow down'));
    renderWithProviders(<IntelligencePanel batchId={7} />);
    fireEvent.click(screen.getByRole('button', { name: 'Analyse import' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/wait a moment/i);
    expect(screen.getByRole('button', { name: 'Analyse import' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Retry analysis/ }));
    await waitFor(() => expect(postInsights).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/Ollama provided the explanation/)).toBeInTheDocument();
  });

  it('degrades gracefully when provider status cannot be loaded', async () => {
    vi.mocked(getAiStatus).mockRejectedValue(new Error('offline'));
    renderWithProviders(<IntelligencePanel batchId={7} />);

    expect(await screen.findByRole('button', { name: 'Analyse import' })).toBeEnabled();
  });

  it('stays disabled for a non-completed batch', async () => {
    renderWithProviders(<IntelligencePanel batchId={7} disabled />);
    expect(screen.getByRole('button', { name: 'Analyse import' })).toBeDisabled();
    expect(screen.getByText(/Analysis becomes available when this import has completed/)).toBeInTheDocument();
    expect(postInsights).not.toHaveBeenCalled();
  });
});
