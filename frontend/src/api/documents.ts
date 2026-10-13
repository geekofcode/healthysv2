import { apiRequest, ApiError } from "./client";
import { selectedOrganization } from "../auth/organizationContext";
import { keycloak } from "../auth/keycloak";
import type { Page } from "./organizations";
import { validAccessToken } from "../auth/keycloak";
import i18n from "../i18n";

export type HealthDocument = {
  id: string;
  documentNumber: string;
  patientId: string;
  categoryId?: string;
  categoryCode?: string;
  categoryName?: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  checksum: string;
  uploadedBy?: string;
  uploadedAt: string;
  status: string;
};
export type DocumentCategory = { id: string; code: string; name: string };
export const documentKeys = {
  list: (patientId: string) => ["documents", patientId] as const,
  categories: ["documents", "categories"] as const,
};
export const listDocuments = (patientId: string, page = 0) =>
  apiRequest<Page<HealthDocument>>(
    `/documents?patientId=${encodeURIComponent(patientId)}&size=20&page=${page}&sort=uploadedAt,desc`,
  );
export const listDocumentCategories = () =>
  apiRequest<DocumentCategory[]>("/documents/categories");
export async function uploadDocument(
  patientId: string,
  categoryId: string,
  file: File,
) {
  const body = new FormData();
  body.append("file", file);
  const params = new URLSearchParams({ patientId });
  if (categoryId) params.set("categoryId", categoryId);
  return apiRequest<HealthDocument>(`/documents?${params}`, {
    method: "POST",
    body,
  });
}
export const archiveDocument = (id: string) =>
  apiRequest<HealthDocument>(`/documents/${id}/archive`, { method: "POST" });
export async function downloadDocument(
  document: Pick<HealthDocument, "id" | "fileName">,
  print = false,
) {
  const token = await validAccessToken();
  const headers = new Headers({
    Authorization: `Bearer ${token}`,
    "Accept-Language": i18n.resolvedLanguage ?? "en",
  });
  const org = selectedOrganization(keycloak?.tokenParsed?.sub);
  if (org) headers.set("X-Organization-ID", org);
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL}/documents/${document.id}/content`,
    { headers },
  );
  if (!response.ok) {
    let message = i18n.t("errors.requestFailed");
    try {
      message = (await response.json()).message ?? message;
    } catch {
      /* Empty error response */
    }
    throw new ApiError(response.status, "DOCUMENT_ACCESS", message);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  if (print) {
    const frame = window.document.createElement("iframe");
    frame.hidden = true;
    frame.src = url;
    frame.onload = () => {
      frame.contentWindow?.focus();
      frame.contentWindow?.print();
    };
    window.document.body.append(frame);
    window.setTimeout(() => {
      frame.remove();
      URL.revokeObjectURL(url);
    }, 60000);
  } else {
    const anchor = window.document.createElement("a");
    anchor.href = url;
    anchor.download = document.fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
