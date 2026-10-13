import { apiRequest } from "./client";
export type ReportFields = {
  reason: string;
  observations: string;
  diagnosis: string;
  treatment: string;
  followUp: string;
};
export type ReportRevision = ReportFields & {
  revision: number;
  validatedAt?: string;
  correctionReason?: string;
};
export type VisitReport = ReportFields & {
  visitId: string;
  episodeId: string;
  patientId: string;
  organizationId: string;
  professionalId: string;
  visitStatus: string;
  visitVersion: number;
  version: number;
  revision: number;
  status: "DRAFT" | "VALIDATED";
  correctionReason?: string;
  revisions: ReportRevision[];
};
export const startCareVisit = (id: string, version: number) =>
  apiRequest(`/care/visits/${id}/start`, {
    method: "POST",
    body: JSON.stringify({ version }),
  });
export const getVisitReport = (id: string) =>
  apiRequest<VisitReport>(`/care/visits/${id}/report`);
export const saveVisitReport = (
  id: string,
  version: number,
  input: ReportFields,
) =>
  apiRequest<VisitReport>(`/care/visits/${id}/report`, {
    method: "PUT",
    body: JSON.stringify({ version, ...input }),
  });
export const finalizeVisitReport = (id: string, version: number) =>
  apiRequest<VisitReport>(`/care/visits/${id}/report/finalize`, {
    method: "POST",
    body: JSON.stringify({ version }),
  });
export const correctVisitReport = (
  id: string,
  version: number,
  correctionReason: string,
) =>
  apiRequest<VisitReport>(`/care/visits/${id}/report/corrections`, {
    method: "POST",
    body: JSON.stringify({ version, correctionReason }),
  });
