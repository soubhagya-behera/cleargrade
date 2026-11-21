export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ImportResponse {
  id: number;
  filename: string;
  status: 'RECEIVED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  rowsRead: number | null;
  imported: number | null;
  newCount: number | null;
  changedCount: number | null;
  unchangedCount: number | null;
  rejected: number | null;
  errorCount: number | null;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  deduplicated: boolean;
}

export type RowErrorCode =
  | 'MISSING_REQUIRED'
  | 'INVALID_FORMAT'
  | 'OUT_OF_RANGE'
  | 'DUPLICATE_ID'
  | 'WRONG_FIELD_COUNT';

export interface RowError {
  lineNumber: number;
  rawStudentId: string | null;
  field: string;
  errorCode: RowErrorCode;
  message: string;
}

export interface StudentSummary {
  studentId: string;
  fullName: string;
  gradeLevel: number;
  section: string;
  percentage: string;
  grade: string;
}

export interface Marks {
  maths: string;
  science: string;
  english: string;
  socialStudies: string;
  secondLanguage: string;
}

export interface StudentReport extends StudentSummary {
  email: string;
  dateOfBirth: string;
  marks: Marks;
  total: string;
  maximum: number;
  passed: boolean;
}

export type InsightEngine = 'GEMINI' | 'OLLAMA' | 'RULE_BASED';
export type InsightSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH';

export interface InsightUsage {
  available: boolean;
  inputTokens: number | null;
  outputTokens: number | null;
  thinkingTokens: number | null;
  totalTokens: number | null;
  modelId: string;
  providerStatus: string;
  interactionStatus: string;
  durationMs: number;
}

export interface AiStatusResponse {
  enabled: boolean;
  geminiConfigured: boolean;
  geminiModel: string;
  ollamaConfigured: boolean;
  ollamaReachable: boolean;
  ollamaModelPresent: boolean;
  ollamaModel: string;
  inferenceTested: boolean;
  deterministicAvailable: boolean;
  defaultEngine: InsightEngine;
  routing: string;
  routingReasons: string[];
  lastAnalysisEngine: string | null;
  lastAnalysisReason: string | null;
  lastAnalysisUsage: InsightUsage;
}


export interface InsightFindingResponse {
  title: string;
  severity: InsightSeverity;
  evidenceMetric: string;
  evidenceValue: string;
  explanation: string;
  recommendedAction: string;
}

export interface MeasuredMetricsResponse {
  rowsRead: number;
  importedCount: number;
  rejectedCount: number;
  newCount: number;
  changedCount: number;
  unchangedCount: number;
  errorCount: number;
  importedPercentage: string;
  rejectedPercentage: string;
  errorsPerRejectedRow: string;
  errorCountsByCode: Record<string, number>;
  schemaColumns: string[];
}

export interface InsightResponse {
  batchId: number;
  engine: InsightEngine;
  engineModel: string;
  fallbackReason: string | null;
  cached: boolean;
  measured: MeasuredMetricsResponse;
  summary: string;
  overallAssessment: string;
  findings: InsightFindingResponse[];
  recommendedActions: string[];
  limitations: string;
  usage: InsightUsage;
  advisoryOnly: boolean;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  details: string[];
  correlationId: string | null;
  path: string;
}
