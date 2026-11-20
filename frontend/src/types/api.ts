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

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  details: string[];
  correlationId: string | null;
  path: string;
}
