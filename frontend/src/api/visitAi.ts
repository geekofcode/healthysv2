import { apiRequest } from "./client";
import type { ReportFields } from "./visitEncounter";
export type AiJobKind = "SUMMARY" | "REPORT" | "TRANSCRIPTION";
export type AiSummary = {
  summary: string;
  sources: { id: string; label: string }[];
  missingData: string[];
};
export type AiJob = {
  id: string;
  kind: AiJobKind;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  output: AiSummary | ReportFields | { text: string } | null;
  obsolete: boolean;
  errorCode?: string;
  createdAt: string;
  model?: string;
};
const base = (visitId: string) => `/care/visits/${visitId}/ai`;
export const listVisitAiJobs = (visitId: string) =>
  apiRequest<AiJob[]>(`${base(visitId)}/jobs`);
export const getVisitAiJob = (visitId: string, jobId: string) =>
  apiRequest<AiJob>(`${base(visitId)}/jobs/${jobId}`);
export const createVisitAiJob = (
  visitId: string,
  kind: "SUMMARY" | "REPORT",
  notes: string | undefined,
  key: string,
) =>
  apiRequest<AiJob>(`${base(visitId)}/jobs`, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify({ kind, ...(notes !== undefined ? { notes } : {}) }),
  });
export const uploadVisitDictation = (
  visitId: string,
  audio: File,
  key: string,
) => {
  const body = new FormData();
  body.append("audio", audio);
  return apiRequest<AiJob>(`${base(visitId)}/dictation`, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body,
  });
};
