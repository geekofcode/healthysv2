// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
const mocks = vi.hoisted(() => ({
  rights: vi.fn(),
  me: vi.fn(),
  doctor: vi.fn(),
  list: vi.fn(),
  create: vi.fn(),
  upload: vi.fn(),
}));
vi.mock("../api/client", () => ({ ApiError: class extends Error {} }));
vi.mock("../api/licensing", () => ({ getEntitlements: mocks.rights }));
vi.mock("../api/persons", () => ({ getMe: mocks.me }));
vi.mock("../api/professionals", () => ({ getProfessional: mocks.doctor }));
vi.mock("../api/visitAi", () => ({
  listVisitAiJobs: mocks.list,
  createVisitAiJob: mocks.create,
  uploadVisitDictation: mocks.upload,
}));
import { CareAiAssistant } from "./CareAiAssistant";
const fields = {
  reason: "Fièvre",
  observations: "Patiente stable",
  diagnosis: "À confirmer",
  treatment: "Hydratation",
  followUp: "Contrôle",
};
function show(onApply = vi.fn(), editable = true) {
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
          },
        })
      }
    >
      <CareAiAssistant
        visitId="v"
        organizationId="o"
        professionalId="d"
        canWrite
        editable={editable}
        fr
        onApply={onApply}
      />
    </QueryClientProvider>,
  );
  return onApply;
}
beforeEach(() => {
  vi.clearAllMocks();
  mocks.rights.mockResolvedValue({ active: true, aiAssistantEnabled: true });
  mocks.me.mockResolvedValue({ id: "person" });
  mocks.doctor.mockResolvedValue({
    personId: "person",
    professionalType: "DOCTOR",
    status: "ACTIVE",
  });
  mocks.list.mockResolvedValue([]);
});
afterEach(cleanup);
it("keeps AI controls absent when the subscription excludes AI", async () => {
  mocks.rights.mockResolvedValue({ active: true, aiAssistantEnabled: false });
  show();
  await waitFor(() => expect(mocks.rights).toHaveBeenCalled());
  expect(screen.queryByText("Assistant IA")).toBeNull();
  expect(mocks.list).not.toHaveBeenCalled();
});
it("does not fetch clinical AI jobs for a doctor who is not assigned to the visit", async () => {
  mocks.doctor.mockResolvedValue({
    personId: "another",
    professionalType: "DOCTOR",
    status: "ACTIVE",
  });
  show();
  await waitFor(() => expect(mocks.doctor).toHaveBeenCalled());
  expect(screen.queryByText("Assistant IA")).toBeNull();
  expect(mocks.list).not.toHaveBeenCalled();
});
it("shows source references, missing data and obsolete warnings without mutating the report", async () => {
  mocks.list.mockResolvedValue([
    {
      id: "s",
      kind: "SUMMARY",
      status: "SUCCEEDED",
      obsolete: true,
      output: {
        summary: "Historique documenté",
        sources: [{ id: "allergy:1", label: "Allergie validée" }],
        missingData: ["Poids absent"],
      },
    },
  ]);
  const apply = show();
  expect(await screen.findByText("Historique documenté")).toBeTruthy();
  expect(screen.getByText(/Allergie validée/)).toBeTruthy();
  expect(screen.getByText("Poids absent")).toBeTruthy();
  expect(screen.getByText(/Résumé obsolète/)).toBeTruthy();
  expect(apply).not.toHaveBeenCalled();
});
it("requires review and an explicit copy action, preserves edited fields and job provenance", async () => {
  mocks.list.mockResolvedValue([
    { id: "r", kind: "REPORT", status: "SUCCEEDED", output: fields },
  ]);
  const apply = show();
  fireEvent.click(
    await screen.findByRole("button", {
      name: "Examiner la proposition de rapport",
    }),
  );
  expect(apply).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Diagnostic proposé"), {
    target: { value: "Diagnostic revu par médecin" },
  });
  fireEvent.click(
    screen.getByRole("button", {
      name: "Copier la proposition dans le brouillon",
    }),
  );
  expect(apply).toHaveBeenCalledWith(
    { ...fields, diagnosis: "Diagnostic revu par médecin" },
    "r",
  );
});
it("takes transcription into editable notes only when requested", async () => {
  mocks.list.mockResolvedValue([
    {
      id: "t",
      kind: "TRANSCRIPTION",
      status: "SUCCEEDED",
      output: { text: "Notes dictées" },
    },
  ]);
  show();
  const notes = await screen.findByLabelText(
    "Notes corrigées pour le compte rendu",
  );
  expect((notes as HTMLTextAreaElement).value).toBe("");
  fireEvent.click(
    await screen.findByRole("button", {
      name: "Reprendre la transcription dans les notes",
    }),
  );
  fireEvent.change(notes, { target: { value: "Notes corrigées" } });
  mocks.create.mockResolvedValue({ id: "j", kind: "REPORT", status: "QUEUED" });
  fireEvent.click(
    screen.getByRole("button", { name: "Proposer un compte rendu" }),
  );
  await waitFor(() =>
    expect(mocks.create).toHaveBeenCalledWith(
      "v",
      "REPORT",
      "Notes corrigées",
      expect.any(String),
    ),
  );
});
it("rejects oversize audio locally and requires explicit upload for an accepted file", async () => {
  show();
  const input = await screen.findByLabelText("Importer un audio");
  const large = new File(["x"], "large.webm", { type: "audio/webm" });
  Object.defineProperty(large, "size", { value: 11 * 1024 * 1024 });
  fireEvent.change(input, { target: { files: [large] } });
  expect(await screen.findByRole("alert")).toHaveProperty(
    "textContent",
    expect.stringContaining("10 Mo"),
  );
  expect(mocks.upload).not.toHaveBeenCalled();
  fireEvent.change(input, {
    target: {
      files: [new File(["audio"], "voice.webm", { type: "audio/webm" })],
    },
  });
  expect(mocks.upload).not.toHaveBeenCalled();
  mocks.upload.mockResolvedValue({
    id: "t",
    kind: "TRANSCRIPTION",
    status: "QUEUED",
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Envoyer pour transcription" }),
  );
  await waitFor(() =>
    expect(mocks.upload).toHaveBeenCalledWith(
      "v",
      expect.any(File),
      expect.any(String),
    ),
  );
});
it("keeps dictation and report actions hidden on a finalized report while allowing preparation summary", async () => {
  show(vi.fn(), false);
  expect(
    await screen.findByRole("button", {
      name: "Préparer le résumé du dossier",
    }),
  ).toBeTruthy();
  expect(
    screen.queryByRole("button", { name: "Enregistrer une dictée" }),
  ).toBeNull();
});
it("reuses the idempotency key after an uncertain network failure", async () => {
  mocks.create
    .mockRejectedValueOnce(new Error("Réseau indisponible"))
    .mockResolvedValueOnce({ id: "s", kind: "SUMMARY", status: "QUEUED" });
  show();
  const button = await screen.findByRole("button", {
    name: "Préparer le résumé du dossier",
  });
  fireEvent.click(button);
  await screen.findByText("Réseau indisponible");
  fireEvent.click(button);
  await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(2));
  expect(mocks.create.mock.calls[0]?.[3]).toBe(mocks.create.mock.calls[1]?.[3]);
});
it("releases microphone tracks when leaving the consultation and sends no audio automatically", async () => {
  const stopTrack = vi.fn();
  const stopRecording = vi.fn();
  const media = { getTracks: () => [{ stop: stopTrack }] };
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia: vi.fn().mockResolvedValue(media) },
  });
  class Recorder {
    static isTypeSupported() {
      return true;
    }
    state = "inactive";
    mimeType = "audio/webm";
    onstop: (() => void) | null = null;
    start() {
      this.state = "recording";
    }
    stop() {
      stopRecording();
      this.state = "inactive";
      this.onstop?.();
    }
  }
  vi.stubGlobal("MediaRecorder", Recorder);
  show();
  fireEvent.click(
    await screen.findByRole("button", { name: "Enregistrer une dictée" }),
  );
  expect(
    await screen.findByRole("button", { name: "Arrêter l’enregistrement" }),
  ).toBeTruthy();
  cleanup();
  expect(stopRecording).toHaveBeenCalled();
  expect(stopTrack).toHaveBeenCalled();
  expect(mocks.upload).not.toHaveBeenCalled();
  vi.unstubAllGlobals();
});
it("prevents applying an obsolete report proposal", async () => {
  mocks.list.mockResolvedValue([
    {
      id: "r",
      kind: "REPORT",
      status: "SUCCEEDED",
      obsolete: true,
      output: fields,
    },
  ]);
  const apply = show();
  expect(
    await screen.findByRole("button", {
      name: "Examiner la proposition de rapport",
    }),
  ).toHaveProperty("disabled", true);
  expect(screen.getByText(/Proposition obsolète/)).toBeTruthy();
  expect(apply).not.toHaveBeenCalled();
});
it("rejects malformed report outputs and aligns note limits with the backend contract", async () => {
  mocks.list.mockResolvedValue([
    {
      id: "r",
      kind: "REPORT",
      status: "SUCCEEDED",
      output: { reason: "Incomplete" },
    },
  ]);
  show();
  expect(
    await screen.findByLabelText("Notes corrigées pour le compte rendu"),
  ).toHaveProperty("maxLength", 30000);
  await waitFor(() => expect(mocks.list).toHaveBeenCalled());
  expect(
    screen.queryByRole("button", {
      name: "Examiner la proposition de rapport",
    }),
  ).toBeNull();
});
