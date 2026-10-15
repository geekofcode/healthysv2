import { apiRequest } from "./client";
export type ReportFields = {
  reason: string;
  observations: string;
  diagnosis: string;
  treatment: string;
  followUp: string;
};
export type ExamRequest={name:string;instructions:string};
export type ReportRevision = ReportFields & {
  examRequests?:ExamRequest[];
  revision: number;
  validatedAt?: string;
  correctionReason?: string;
  aiJobId?: string;
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
  aiJobId?: string;
  revisions: ReportRevision[];
  examRequests?:ExamRequest[];followUpVisitId?:string;
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
  input: ReportFields & {examRequests?:ExamRequest[]},
  aiJobId?: string,
) =>
  apiRequest<VisitReport>(`/care/visits/${id}/report`, {
    method: "PUT",
    body: JSON.stringify({
      version,
      ...input,
      ...(aiJobId ? { aiJobId } : {}),
    }),
  });
export const finalizeVisitReport = (id: string, version: number,followUp?:{followUpAt:string;followUpProfessionalId?:string}) =>
  apiRequest<VisitReport>(`/care/visits/${id}/report/finalize`, {
    method: "POST",
    body: JSON.stringify({ version,...followUp }),
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
