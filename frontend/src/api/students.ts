import { apiClient } from './client';
import type { Page, StudentReport, StudentSummary } from '../types/api';

export interface StudentQuery {
  query?: string;
  grade?: number;
  section?: string;
  page: number;
  size: number;
  sort?: string;
}

export const studentKeys = {
  list: (query: StudentQuery) => ['students', query] as const,
  report: (studentId: string) => ['students', 'report', studentId] as const,
};

export function searchStudents(query: StudentQuery, signal?: AbortSignal): Promise<Page<StudentSummary>> {
  return apiClient.get<Page<StudentSummary>>('/students', { ...query, sort: query.sort ?? 'studentId,asc' }, signal);
}

export function getStudent(studentId: string, signal?: AbortSignal): Promise<StudentReport> {
  return apiClient.get<StudentReport>(`/students/${encodeURIComponent(studentId)}`, undefined, signal);
}
