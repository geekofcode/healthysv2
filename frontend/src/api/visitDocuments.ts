import { apiRequest } from "./client";
export type PrescriptionItem = {
  medication: string;
  dosage: string;
  route: string;
  frequency: string;
  duration: string;
  instructions: string;
};
export type VisitPrescription = {
  visitId: string;
  version: number;
  currentRevision: number;
  status: string;
  items: PrescriptionItem[];
  correctionReason?: string;
  validatedAt?: string;
};
export type VisitDocument = {
  id: string;
  visitId: string;
  type: "REPORT" | "PRESCRIPTION";
  reportRevision: number;
  prescriptionRevision?: number;
  fileName: string;
  createdAt: string;
};
const path = (id: string) => `/care/visits/${id}`;
export const getVisitPrescription = (id: string) =>
  apiRequest<VisitPrescription>(`${path(id)}/prescription`);
export const saveVisitPrescription = (
  id: string,
  input: {
    expectedVersion: number;
    items: PrescriptionItem[];
    correctionReason?: string;
  },
) =>
  apiRequest<VisitPrescription>(`${path(id)}/prescription`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
export const finalizeVisitPrescription = (
  id: string,
  expectedVersion: number,
) =>
  apiRequest<VisitPrescription>(`${path(id)}/prescription/finalize`, {
    method: "POST",
    body: JSON.stringify({ expectedVersion }),
  });
export const correctVisitPrescription = (
  id: string,
  expectedVersion: number,
  reason: string,
) =>
  apiRequest<VisitPrescription>(`${path(id)}/prescription/corrections`, {
    method: "POST",
    body: JSON.stringify({ expectedVersion, reason }),
  });
export const listVisitDocuments = (id: string) =>
  apiRequest<VisitDocument[]>(`${path(id)}/documents`);
export const generateVisitDocument = (
  id: string,
  type: VisitDocument["type"],
) =>
  apiRequest<VisitDocument>(`${path(id)}/documents`, {
    method: "POST",
    body: JSON.stringify({ type }),
  });
export type PrescriptionRevision = {
  id: string;
  revision: number;
  items: PrescriptionItem[];
  correctionReason?: string;
  validatedAt: string;
  validatedBy: string;
};
export const listPrescriptionRevisions = (id: string) =>
  apiRequest<PrescriptionRevision[]>(
    `/care/visits/${id}/prescription/revisions`,
  );
