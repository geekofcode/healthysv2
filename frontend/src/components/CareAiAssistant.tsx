import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getEntitlements } from "../api/licensing";
import { getMe } from "../api/persons";
import { getProfessional } from "../api/professionals";
import {
  createVisitAiJob,
  listVisitAiJobs,
  uploadVisitDictation,
  type AiJob,
  type AiSummary,
} from "../api/visitAi";
import type { ReportFields } from "../api/visitEncounter";
import { ApiErrorMessage } from "./ApiErrorMessage";

const MAX_AUDIO_BYTES = 10 * 1024 * 1024;
const MAX_AUDIO_MS = 5 * 60 * 1000;
const POLL_WINDOW_MS = 5 * 60 * 1000;
const reportLabels: Record<keyof ReportFields, [string, string]> = {
  reason: ["Motif proposé", "Proposed reason"],
  observations: ["Observations proposées", "Proposed observations"],
  diagnosis: ["Diagnostic proposé", "Proposed diagnosis"],
  treatment: ["Traitement proposé", "Proposed treatment"],
  followUp: ["Suivi proposé", "Proposed follow-up"],
};
const validReport = (value: unknown): value is ReportFields =>
  !!value &&
  typeof value === "object" &&
  Object.entries(reportLabels).every(([field]) => {
    const text = (value as Record<string, unknown>)[field];
    return (
      typeof text === "string" &&
      text.length <=
        (field === "reason" ? 10000 : field === "observations" ? 50000 : 20000)
    );
  });
const pending = (job: AiJob) =>
  job.status === "QUEUED" || job.status === "RUNNING";

type Props = {
  visitId: string;
  organizationId: string;
  professionalId: string;
  canWrite: boolean;
  editable: boolean;
  fr: boolean;
  onApply: (fields: ReportFields, jobId: string) => void;
};
export function CareAiAssistant(props: Props) {
  const { organizationId, professionalId, canWrite } = props;
  const rights = useQuery({
    queryKey: ["licensing", organizationId],
    queryFn: () => getEntitlements(organizationId),
    enabled: canWrite && !!organizationId,
    retry: false,
  });
  const eligible =
    canWrite &&
    rights.data?.active === true &&
    rights.data.aiAssistantEnabled === true;
  const me = useQuery({
    queryKey: ["persons", "me"],
    queryFn: getMe,
    enabled: eligible,
    retry: false,
  });
  const doctor = useQuery({
    queryKey: ["professionals", professionalId],
    queryFn: () => getProfessional(professionalId),
    enabled: eligible && !!professionalId,
    retry: false,
  });
  // Identity matching hides the controls for doctors who can read a record but are not assigned this visit.
  if (
    !eligible ||
    !me.data ||
    !doctor.data ||
    me.data.id !== doctor.data.personId ||
    doctor.data.status !== "ACTIVE" ||
    !["DOCTOR", "MEDECIN"].includes(doctor.data.professionalType)
  )
    return null;
  return <AssistantWorkspace {...props} />;
}

function AssistantWorkspace({ visitId, editable, fr, onApply }: Props) {
  const cache = useQueryClient();
  const queryKey = ["visit-ai", visitId];
  const [pollUntil, setPollUntil] = useState(Date.now() + POLL_WINDOW_MS);
  const jobs = useQuery({
    queryKey,
    queryFn: () => listVisitAiJobs(visitId),
    retry: false,
    refetchInterval: (query) =>
      Date.now() < pollUntil && query.state.data?.some(pending) ? 2500 : false,
  });
  const [notes, setNotes] = useState("");
  const [proposal, setProposal] = useState<{
    fields: ReportFields;
    jobId: string;
  } | null>(null);
  const [recording, setRecording] = useState(false);
  const [acquiring, setAcquiring] = useState(false);
  const [localError, setLocalError] = useState("");
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const stopTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mounted = useRef(true);
  const request = useRef<{ fingerprint: string; key: string } | null>(null);
  const keyFor = (fingerprint: string) => {
    if (request.current?.fingerprint !== fingerprint)
      request.current = { fingerprint, key: crypto.randomUUID() };
    return request.current.key;
  };
  const remember = (job: AiJob) => {
    request.current = null;
    setPollUntil(Date.now() + POLL_WINDOW_MS);
    cache.setQueryData<AiJob[]>(queryKey, (old) => [
      job,
      ...(old ?? []).filter((item) => item.id !== job.id),
    ]);
    void cache.invalidateQueries({ queryKey });
  };
  const generate = useMutation({
    mutationFn: (kind: "SUMMARY" | "REPORT") =>
      createVisitAiJob(
        visitId,
        kind,
        kind === "REPORT" ? notes : undefined,
        keyFor(`${kind}:${kind === "REPORT" ? notes : ""}`),
      ),
    onSuccess: remember,
  });
  const upload = useMutation({
    mutationFn: (audio: File) =>
      uploadVisitDictation(
        visitId,
        audio,
        keyFor(
          `TRANSCRIPTION:${audio.name}:${audio.size}:${audio.lastModified}`,
        ),
      ),
    onSuccess: (job) => {
      remember(job);
      setAudioFile(null);
    },
  });
  const busy = generate.isPending || upload.isPending;

  const release = () => {
    if (stopTimer.current) clearTimeout(stopTimer.current);
    stopTimer.current = null;
    stream.current?.getTracks().forEach((track) => track.stop());
    stream.current = null;
  };
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      if (recorder.current && recorder.current.state !== "inactive") {
        recorder.current.onstop = null;
        recorder.current.stop();
      }
      release();
    };
  }, []);
  const acceptAudio = (file: File | null) => {
    setLocalError("");
    setAudioFile(null);
    if (!file) return;
    if (!file.size || file.size > MAX_AUDIO_BYTES) {
      setLocalError(
        fr
          ? "Le fichier audio doit contenir des données et ne pas dépasser 10 Mo."
          : "Audio must contain data and must not exceed 10 MiB.",
      );
      return;
    }
    if (!/^audio\//.test(file.type)) {
      setLocalError(
        fr ? "Choisissez un fichier audio." : "Choose an audio file.",
      );
      return;
    }
    setAudioFile(file);
  };
  const record = async () => {
    setAcquiring(true);
    setLocalError("");
    let acquired: MediaStream | null = null;
    try {
      if (!navigator.mediaDevices?.getUserMedia || !globalThis.MediaRecorder)
        throw new Error(
          fr
            ? "Enregistrement indisponible : importez un fichier audio."
            : "Recording unavailable: upload an audio file.",
        );
      acquired = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!mounted.current) {
        acquired.getTracks().forEach((track) => track.stop());
        return;
      }
      stream.current = acquired;
      const mimeType = [
        "audio/webm;codecs=opus",
        "audio/mp4",
        "audio/ogg;codecs=opus",
      ].find((type) => MediaRecorder.isTypeSupported(type));
      const capture = new MediaRecorder(
        acquired,
        mimeType ? { mimeType } : undefined,
      );
      recorder.current = capture;
      const chunks: Blob[] = [];
      let bytes = 0;
      capture.ondataavailable = (event) => {
        if (!event.data.size) return;
        chunks.push(event.data);
        bytes += event.data.size;
        if (bytes > MAX_AUDIO_BYTES && capture.state !== "inactive")
          capture.stop();
      };
      capture.onerror = () => {
        if (mounted.current) {
          setLocalError(
            fr
              ? "Enregistrement interrompu. Réessayez ou importez un fichier."
              : "Recording interrupted. Retry or upload a file.",
          );
          setRecording(false);
        }
        release();
      };
      capture.onstop = () => {
        release();
        if (!mounted.current) return;
        setRecording(false);
        const blob = new Blob(chunks, {
          type: capture.mimeType || "audio/webm",
        });
        acceptAudio(
          new File(
            [blob],
            `dictation.${blob.type.includes("mp4") ? "m4a" : blob.type.includes("ogg") ? "ogg" : "webm"}`,
            { type: blob.type },
          ),
        );
      };
      capture.start(1000);
      setRecording(true);
      stopTimer.current = setTimeout(() => {
        if (capture.state !== "inactive") capture.stop();
      }, MAX_AUDIO_MS);
    } catch (error) {
      acquired?.getTracks().forEach((track) => track.stop());
      release();
      setLocalError(
        error instanceof Error
          ? error.message
          : fr
            ? "Accès microphone refusé."
            : "Microphone access denied.",
      );
    } finally {
      if (mounted.current) setAcquiring(false);
    }
  };
  const summary = jobs.data?.find(
    (job) => job.kind === "SUMMARY" && job.status === "SUCCEEDED",
  );
  const summaryOutput = summary?.output as AiSummary | null;
  const report = jobs.data?.find(
    (job) =>
      job.kind === "REPORT" &&
      job.status === "SUCCEEDED" &&
      validReport(job.output),
  );
  const proposalReady =
    !!proposal &&
    validReport(proposal.fields) &&
    !!proposal.fields.reason.trim() &&
    !!jobs.data?.some(
      (job) =>
        job.id === proposal.jobId &&
        job.status === "SUCCEEDED" &&
        !job.obsolete,
    );
  const transcription = jobs.data?.find(
    (job) => job.kind === "TRANSCRIPTION" && job.status === "SUCCEEDED",
  );
  return (
    <section className="card" aria-label={fr ? "Assistant IA" : "AI assistant"}>
      <h2>{fr ? "Assistant IA" : "AI assistant"}</h2>
      <p>
        {fr
          ? "Les propositions sont à vérifier et à corriger. La validation du rapport reste une action du médecin."
          : "Review and correct every proposal. The doctor validates the report."}
      </p>
      <button disabled={busy} onClick={() => generate.mutate("SUMMARY")}>
        {fr ? "Préparer le résumé du dossier" : "Prepare record summary"}
      </button>
      {summaryOutput && (
        <article>
          <h3>
            {fr ? "Résumé avant consultation" : "Pre-consultation summary"}
          </h3>
          {summary?.obsolete && (
            <p role="status">
              {fr
                ? "Résumé obsolète : le dossier a changé. Générez une nouvelle synthèse."
                : "Outdated summary: the record changed. Generate a new summary."}
            </p>
          )}
          <p style={{ whiteSpace: "pre-wrap" }}>{summaryOutput.summary}</p>
          <h4>{fr ? "Sources du dossier" : "Record sources"}</h4>
          <ul>
            {summaryOutput.sources?.map((source) => (
              <li key={source.id}>
                {source.label} · {source.id}
              </li>
            ))}
          </ul>
          {!!summaryOutput.missingData?.length && (
            <>
              <h4>{fr ? "Données manquantes" : "Missing data"}</h4>
              <ul>
                {summaryOutput.missingData.map((missing, index) => (
                  <li key={index}>{missing}</li>
                ))}
              </ul>
            </>
          )}
        </article>
      )}
      {editable && (
        <>
          <h3>
            {fr ? "Notes et dictée médicale" : "Notes and medical dictation"}
          </h3>
          <p>
            {fr
              ? "Enregistrement limité à 5 minutes et 10 Mo. L’audio temporaire sera purgé après transcription."
              : "Recording limited to 5 minutes and 10 MiB. Temporary audio is purged after transcription."}
          </p>
          <button
            disabled={(busy && !recording) || acquiring}
            onClick={() =>
              recording ? recorder.current?.stop() : void record()
            }
          >
            {recording
              ? fr
                ? "Arrêter l’enregistrement"
                : "Stop recording"
              : fr
                ? "Enregistrer une dictée"
                : "Record dictation"}
          </button>
          <label>
            {fr ? "Importer un audio" : "Upload audio"}
            <input
              type="file"
              accept="audio/*"
              disabled={recording || acquiring || busy}
              onChange={(event) => acceptAudio(event.target.files?.[0] ?? null)}
            />
          </label>
          {audioFile && (
            <div>
              <p>{audioFile.name}</p>
              <button
                disabled={busy || recording || acquiring}
                onClick={() => upload.mutate(audioFile)}
              >
                {fr ? "Envoyer pour transcription" : "Send for transcription"}
              </button>
              <button disabled={busy} onClick={() => setAudioFile(null)}>
                {fr ? "Supprimer cet audio local" : "Discard local audio"}
              </button>
            </div>
          )}
          {transcription && (
            <article>
              <p style={{ whiteSpace: "pre-wrap" }}>
                {(transcription.output as { text: string } | null)?.text}
              </p>
              <button
                disabled={busy}
                onClick={() =>
                  setNotes((transcription.output as { text: string }).text)
                }
              >
                {fr
                  ? "Reprendre la transcription dans les notes"
                  : "Use transcription in notes"}
              </button>
            </article>
          )}
          <label>
            {fr
              ? "Notes corrigées pour le compte rendu"
              : "Corrected notes for the report"}
            <textarea
              maxLength={30000}
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
            />
          </label>
          <button
            disabled={busy || recording || acquiring || !notes.trim()}
            onClick={() => generate.mutate("REPORT")}
          >
            {fr ? "Proposer un compte rendu" : "Suggest a report"}
          </button>
          {report && (
            <button
              disabled={busy || report.obsolete}
              onClick={() =>
                setProposal({
                  fields: { ...(report.output as ReportFields) },
                  jobId: report.id,
                })
              }
            >
              {fr
                ? "Examiner la proposition de rapport"
                : "Review report proposal"}
            </button>
          )}
          {report?.obsolete && (
            <p role="status">
              {fr
                ? "Proposition obsolète : générez un nouveau compte rendu avant de la reprendre."
                : "Outdated proposal: generate a new report before using it."}
            </p>
          )}
          {proposal && (
            <fieldset>
              <legend>
                {fr ? "Proposition IA à corriger" : "AI proposal to correct"}
              </legend>
              {(Object.keys(reportLabels) as (keyof ReportFields)[]).map(
                (field) => (
                  <label key={field}>
                    {reportLabels[field][fr ? 0 : 1]}
                    <textarea
                      value={proposal.fields[field] ?? ""}
                      maxLength={
                        field === "reason"
                          ? 10000
                          : field === "observations"
                            ? 50000
                            : 20000
                      }
                      onChange={(event) =>
                        setProposal({
                          ...proposal,
                          fields: {
                            ...proposal.fields,
                            [field]: event.target.value,
                          },
                        })
                      }
                    />
                  </label>
                ),
              )}
              <button
                disabled={!proposalReady}
                onClick={() => {
                  if (proposalReady) onApply(proposal.fields, proposal.jobId);
                }}
              >
                {fr
                  ? "Copier la proposition dans le brouillon"
                  : "Copy proposal into draft"}
              </button>
            </fieldset>
          )}
        </>
      )}
      {jobs.data?.some(pending) && (
        <p role="status">
          {fr
            ? "Traitement IA en cours. Vous pouvez poursuivre la consultation manuellement."
            : "AI processing. You can continue the consultation manually."}
        </p>
      )}
      {jobs.data?.some(pending) && Date.now() >= pollUntil && (
        <button
          onClick={() => {
            setPollUntil(Date.now() + POLL_WINDOW_MS);
            void jobs.refetch();
          }}
        >
          {fr ? "Actualiser les traitements" : "Refresh jobs"}
        </button>
      )}
      {jobs.data
        ?.filter((job) => job.status === "FAILED" || job.status === "CANCELLED")
        .map((job) => (
          <p key={job.id} role="status">
            {fr ? "Traitement IA non abouti" : "AI processing unsuccessful"} :{" "}
            {job.kind} · {job.errorCode ?? job.status}
          </p>
        ))}
      {localError && <p role="alert">{localError}</p>}
      {[jobs.error, generate.error, upload.error]
        .filter(Boolean)
        .map((error, index) => (
          <ApiErrorMessage key={index} error={error} />
        ))}
    </section>
  );
}
