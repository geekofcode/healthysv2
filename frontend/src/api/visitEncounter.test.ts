import { expect, it, vi } from "vitest";
vi.mock("./client", () => ({ apiRequest: vi.fn().mockResolvedValue({}) }));
import { apiRequest } from "./client";
import {
  correctVisitReport,
  saveVisitReport,
  startCareVisit,
} from "./visitEncounter";
import { createCareFollowUp, saveVisitIntake } from "./careEpisodes";
import { closeCash } from "./cashManagement";
it("keeps visit version separate from report version and records correction reason", () => {
  startCareVisit("visit", 3);
  saveVisitReport("visit", 7, {
    reason: "r",
    observations: "o",
    diagnosis: "d",
    treatment: "t",
    followUp: "f",
  });
  correctVisitReport("visit", 8, "Correction diagnostic");
  expect(apiRequest).toHaveBeenCalledWith(
    "/care/visits/visit/start",
    expect.objectContaining({ body: '{"version":3}' }),
  );
  expect(apiRequest).toHaveBeenCalledWith(
    "/care/visits/visit/report/corrections",
    expect.objectContaining({
      body: '{"version":8,"correctionReason":"Correction diagnostic"}',
    }),
  );
});
it("creates follow-up under existing episode and stores intake per visit", () => {
  createCareFollowUp(
    "episode",
    {
      professionalId: "doctor",
      scheduledAt: "2026-10-20T10:00:00Z",
    },
    "followup-key",
  );
  saveVisitIntake("visit", { observations: "o", vitals: { temperatureC: 38 } });
  expect(apiRequest).toHaveBeenCalledWith(
    "/care/episodes/episode/follow-ups",
    expect.objectContaining({ method: "POST", headers:{"Idempotency-Key":"followup-key"} }),
  );
  expect(apiRequest).toHaveBeenCalledWith(
    "/care/visits/visit/intake",
    expect.objectContaining({ method: "PUT" }),
  );
});
it("sends closure idempotency key and keeps declared currencies separate", () => {
  closeCash(
    {
      from: "start",
      to: "end",
      declaredAmounts: [
        { currency: "CAD", amount: 10 },
        { currency: "EUR", amount: 12 },
      ],
    },
    "stable-key",
  );
  expect(apiRequest).toHaveBeenCalledWith(
    "/billing/care/cash-closures",
    expect.objectContaining({
      headers: { "Idempotency-Key": "stable-key" },
      body: JSON.stringify({
        from: "start",
        to: "end",
        declaredAmounts: [
          { currency: "CAD", amount: 10 },
          { currency: "EUR", amount: 12 },
        ],
      }),
    }),
  );
});
