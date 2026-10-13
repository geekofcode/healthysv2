import { beforeEach, expect, it, vi } from "vitest";
const request = vi.hoisted(() => vi.fn().mockResolvedValue({ id: "j" }));
vi.mock("./client", () => ({ apiRequest: request }));
import {
  createVisitAiJob,
  getVisitAiJob,
  listVisitAiJobs,
  uploadVisitDictation,
} from "./visitAi";
beforeEach(() => request.mockClear());
it("uses visit-scoped endpoints and idempotency keys without embedding clinical notes in URLs", async () => {
  await createVisitAiJob("v", "REPORT", "Medical notes", "request-key");
  expect(request).toHaveBeenCalledWith("/care/visits/v/ai/jobs", {
    method: "POST",
    headers: { "Idempotency-Key": "request-key" },
    body: JSON.stringify({ kind: "REPORT", notes: "Medical notes" }),
  });
  await getVisitAiJob("v", "j");
  expect(request).toHaveBeenLastCalledWith("/care/visits/v/ai/jobs/j");
  await listVisitAiJobs("v");
  expect(request).toHaveBeenLastCalledWith("/care/visits/v/ai/jobs");
});
it("sends temporary dictation as multipart without forcing a content type", async () => {
  const audio = new File(["audio"], "dictation.webm", { type: "audio/webm" });
  await uploadVisitDictation("v", audio, "audio-key");
  const [path, options] = request.mock.calls[0]!;
  expect(path).toBe("/care/visits/v/ai/dictation");
  expect(options.headers).toEqual({ "Idempotency-Key": "audio-key" });
  expect(options.body.get("audio").name).toBe("dictation.webm");
});
