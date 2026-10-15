vi.mock('../components/ClinicalMeasurements',()=>({ClinicalMeasurements:()=>null}));
vi.mock("../api/careHistory",()=>({getCarePatientHistory:vi.fn().mockResolvedValue([])}));
// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
const mock = vi.hoisted(() => ({
  start: vi.fn().mockResolvedValue({}),
  save: vi.fn().mockResolvedValue({}),
  finalize: vi.fn().mockResolvedValue({}),
  report: {
    visitId: "v",
    episodeId: "e",
    patientId: "p",
    professionalId: "d",
    visitStatus: "SCHEDULED",
    visitVersion: 4,
    version: 2,
    revision: 1,
    status: "DRAFT",
    reason: "Fièvre",
    observations: "",
    diagnosis: "Diagnostic",
    treatment: "",
    followUp: "",
    revisions: [],
  },
}));
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ hasAnyRole: () => true }),
}));
vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t:(key:string)=>key,i18n: { language: "fr" } }),
}));
vi.mock("../api/client", () => ({ ApiError: class extends Error {} }));
vi.mock("../api/visitEncounter", () => ({
  getVisitReport: vi.fn(async () => mock.report),
  startCareVisit: mock.start,
  saveVisitReport: mock.save,
  finalizeVisitReport: mock.finalize,
  correctVisitReport: vi.fn(),
}));
vi.mock("../api/patients", () => ({
  getPatient: vi.fn().mockRejectedValue(new Error("Accès clinique refusé")),
}));
vi.mock("../api/careEpisodes", () => ({
  getReceptionOptions:vi.fn().mockResolvedValue({doctors:[],specialities:[]}),
  getCareEpisode: vi.fn().mockResolvedValue({}),
  getVisitIntake: vi.fn().mockResolvedValue({
    observations: "Constantes accueil",
    vitals: { temperatureC: 38 },
  }),
}));
vi.mock("../components/CareAiAssistant", () => ({
  CareAiAssistant: ({
    onApply,
  }: {
    onApply: (fields: object, jobId: string) => void;
  }) => (
    <button
      onClick={() =>
        onApply(
          {
            reason: "Motif IA",
            observations: "Notes IA",
            diagnosis: "Diagnostic IA",
            treatment: "Traitement IA",
            followUp: "Suivi IA",
          },
          "job-ai",
        )
      }
    >
      Copier une proposition IA
    </button>
  ),
}));
vi.mock("../components/VisitPrescription", () => ({
  VisitPrescription: () => null,
}));
vi.mock("../api/visitDocuments", () => ({
  listVisitDocuments: vi.fn().mockResolvedValue([]),
  generateVisitDocument: vi.fn(),
}));
vi.mock("../api/documents", () => ({ downloadDocument: vi.fn() }));
import { CareVisitPage } from "./CareVisitPage";
function show() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={["/care/visits/v"]}>
        <Routes>
          <Route path="/care/visits/:id" element={<CareVisitPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
it("starts the existing visit with its own version and keeps denied clinical data hidden", async () => {
  mock.report.visitStatus = "SCHEDULED";
  show();
  fireEvent.click(
    await screen.findByRole("button", {
      name: "Démarrer la rencontre médicale",
    }),
  );
  await waitFor(() => expect(mock.start).toHaveBeenCalledWith("v", 4));
  expect(await screen.findByText("Accès clinique refusé")).toBeTruthy();
  expect(screen.queryByText("Ouvrir le dossier patient")).toBeNull();
  expect(await screen.findByText("Constantes accueil")).toBeTruthy();
});
it("requires saving edited report before validation and sends report version", async () => {
  mock.report.visitStatus = "IN_PROGRESS";
  show();
  const textarea = await screen.findByLabelText("Observations");
  fireEvent.change(textarea, { target: { value: "Nouvelles observations" } });
  expect(
    (
      screen.getByRole("button", {
        name: "Valider le rapport et terminer la visite",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.click(
    screen.getByRole("button", { name: "Enregistrer le brouillon" }),
  );
  await waitFor(() =>
    expect(mock.save).toHaveBeenCalledWith(
      "v",
      2,
      expect.objectContaining({ observations: "Nouvelles observations" }),
    ),
  );
  expect(mock.finalize).not.toHaveBeenCalled();
});

it("keeps AI provenance when the copied proposal is corrected and saved", async () => {
  mock.report.visitStatus = "IN_PROGRESS";
  show();
  fireEvent.click(
    await screen.findByRole("button", { name: "Copier une proposition IA" }),
  );
  fireEvent.change(screen.getByLabelText("Diagnostic"), {
    target: { value: "Diagnostic corrigé" },
  });
  expect(
    screen.getByRole("button", {
      name: "Valider le rapport et terminer la visite",
    }),
  ).toHaveProperty("disabled", true);
  fireEvent.click(
    screen.getByRole("button", { name: "Enregistrer le brouillon" }),
  );
  await waitFor(() =>
    expect(mock.save).toHaveBeenCalledWith(
      "v",
      2,
      expect.objectContaining({
        diagnosis: "Diagnostic corrigé",
        reason: "Motif IA",
      }),
      "job-ai",
    ),
  );
  expect(mock.finalize).not.toHaveBeenCalled();
});
