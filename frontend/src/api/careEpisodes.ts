import { apiRequest } from "./client";
export type Intake = {
  observations?: string;
  measurements?:import("./patientHealth").DatedMeasurement[];
  vitals: Partial<
    Record<
      | "temperatureC"
      | "systolicBp"
      | "diastolicBp"
      | "heartRate"
      | "respiratoryRate"
      | "oxygenSaturation"
      | "weightKg"
      | "heightCm",
      number
    >
  >;
};
export type CareEpisode = {
  id: string;
  patientId: string;
  organizationId: string;
  reason: string;
  specialityId?: string;
  status: string;
  createdAt: string;
  startedAt?: string;
  expiresAt?: string;
  followUpSeconds: number;
  tariffId?:string;tariffPrice?:number;tariffCurrency?:string;tariffTaxRate?:number;
  visits: {
    id: string;
    professionalId?: string;
    scheduledAt?: string;
    status: string;
    followUp: boolean;
    version: number;
    startedAt?: string;
  }[];
  intake?: Intake;
};
export const createCareEpisode = (input: {
  patientId: string;
  reason: string;
  specialityId: string;
  professionalId: string;
  scheduledAt: string;
}) =>
  apiRequest<CareEpisode>("/care/episodes", {
    method: "POST",
    body: JSON.stringify(input),
  });
export const getCareEpisode = (id: string) =>
  apiRequest<CareEpisode>(`/care/episodes/${id}`);
export const listCareEpisodes = (patientId: string) =>
  apiRequest<CareEpisode[]>(
    `/care/episodes?patientId=${encodeURIComponent(patientId)}`,
  );
export const saveCareIntake = (id: string, input: Intake) =>
  apiRequest<CareEpisode>(`/care/episodes/${id}/intake`, {
    method: "PUT",
    body: JSON.stringify(input),
  });

export const getCareIntake = (id: string) =>
  apiRequest<Intake | null>(`/care/episodes/${id}/intake`);

export type ReceptionOptions = {
  doctors: {
    professionalId: string;
    firstName: string;
    lastName: string;
    specialityIds: string[];
  }[];
  specialities: { id: string; name: string }[];
};
export const getReceptionOptions = () =>
  apiRequest<ReceptionOptions>("/care/episodes/reception-options");

export type VisitScheduleInput = {
  professionalId: string;
  scheduledAt: string;
  version: number;
  reason?: string;
};
export type CareVisitHistory = {
  id: string;
  action?: string;
  previousProfessionalId?: string;
  professionalId?: string;
  previousScheduledAt?: string;
  scheduledAt?: string;
  occurredAt?: string;
  changedAt?: string;
  reason?: string;
  status?: string;
  createdAt?: string;
};
export type ScheduledCareVisit = {
  id: string;
  episodeId: string;
  patientId: string;
  organizationId: string;
  professionalId: string;
  scheduledAt: string;
  status: string;
  followUp: boolean;
  version: number;
  createdAt: string;
  history: CareVisitHistory[];
};
export const scheduleCareVisit = (id: string, input: VisitScheduleInput) =>
  apiRequest<ScheduledCareVisit>(`/care/visits/${id}/schedule`, {
    method: "PUT",
    body: JSON.stringify(input),
  });

export const getScheduledCareVisit = (id: string) =>
  apiRequest<ScheduledCareVisit>(`/care/visits/${id}`);
export const createCareFollowUp = (
  id: string,
  input: { professionalId: string; scheduledAt: string; reason?: string },
  key: string,
) =>
  apiRequest<ScheduledCareVisit>(`/care/episodes/${id}/follow-ups`, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(input),
  });
export const getVisitIntake = (id: string) =>
  apiRequest<Intake | null>(`/care/visits/${id}/intake`);
export const saveVisitIntake = (id: string, input: Intake) =>
  apiRequest<Intake>(`/care/visits/${id}/intake`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
