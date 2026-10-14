import {useHospitalPermission} from '../auth/HospitalPermissions';
import {CarePatientHistory} from "../components/CarePatientHistory";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { ApiErrorMessage } from "../components/ApiErrorMessage";
import { CareAiAssistant } from "../components/CareAiAssistant";
import { VisitPrescription } from "../components/VisitPrescription";
import { getPatient } from "../api/patients";
import { getCareEpisode, getVisitIntake } from "../api/careEpisodes";
import {
  correctVisitReport,
  finalizeVisitReport,
  getVisitReport,
  saveVisitReport,
  startCareVisit,
  type ReportFields,
  type VisitReport,
} from "../api/visitEncounter";
import {
  generateVisitDocument,
  listVisitDocuments,
  type VisitDocument,
} from "../api/visitDocuments";
import { downloadDocument } from "../api/documents";
const fields: Record<keyof ReportFields, [string, string]> = {
  reason: ["Motif", "Reason"],
  observations: ["Observations", "Observations"],
  diagnosis: ["Diagnostic", "Diagnosis"],
  treatment: ["Traitement", "Treatment"],
  followUp: ["Suivi et recommandations", "Follow-up and recommendations"],
};
const intakeNames: Record<string, [string, string]> = {
  temperatureC: ["Température (°C)", "Temperature (°C)"],
  systolicBp: ["Pression systolique (mmHg)", "Systolic pressure (mmHg)"],
  diastolicBp: ["Pression diastolique (mmHg)", "Diastolic pressure (mmHg)"],
  heartRate: ["Fréquence cardiaque (/min)", "Heart rate (/min)"],
  respiratoryRate: ["Fréquence respiratoire (/min)", "Respiratory rate (/min)"],
  oxygenSaturation: ["Saturation O₂ (%)", "O₂ saturation (%)"],
  weightKg: ["Poids (kg)", "Weight (kg)"],
  heightCm: ["Taille (cm)", "Height (cm)"],
};
export function CareVisitPage() {
  const { id = "" } = useParams();
  const { i18n } = useTranslation();
  const fr = i18n.language.startsWith("fr");
  const auth = useAuth();const reportUpdate=useHospitalPermission('reports','UPDATE');
  const report = useQuery({
    queryKey: ["visit-report", id],
    queryFn: () => getVisitReport(id),
  });
  if (report.isPending) return <p>…</p>;
  if (report.isError) return <ApiErrorMessage error={report.error} />;
  return (
    <VisitWorkspace
      key={`${id}-${report.data.version}-${report.data.visitVersion}-${report.data.status}`}
      id={id}
      fr={fr}
      value={report.data}
      canWrite={reportUpdate&&auth.hasAnyRole("DOCTOR")}
    />
  );
}
function VisitWorkspace({
  id,
  fr,
  value,
  canWrite,
}: {
  id: string;
  fr: boolean;
  value: VisitReport;
  canWrite: boolean;
}) {
  const documentCreate=useHospitalPermission('documents','CREATE');
  const cache = useQueryClient();
  const [draft, setDraft] = useState<ReportFields>({
    reason: value.reason ?? "",
    observations: value.observations ?? "",
    diagnosis: value.diagnosis ?? "",
    treatment: value.treatment ?? "",
    followUp: value.followUp ?? "",
  });
  const [aiJobId, setAiJobId] = useState<string | undefined>(value.aiJobId);
  const [correctionReason, setCorrectionReason] = useState("");
  const patient = useQuery({
    queryKey: ["patients", value.patientId],
    queryFn: () => getPatient(value.patientId),
    retry: false,
  });
  const intake = useQuery({
    queryKey: ["visit-intake", id],
    queryFn: () => getVisitIntake(id),
    retry: false,
  });
  const episode = useQuery({
    queryKey: ["care-episodes", value.episodeId],
    queryFn: () => getCareEpisode(value.episodeId),
  });
  const refresh = () => {
    void cache.invalidateQueries({ queryKey: ["visit-report", id] });
    void cache.invalidateQueries({ queryKey: ["care-calendar"] });
    void cache.invalidateQueries({ queryKey: ["patients", value.patientId] });
    void cache.invalidateQueries({
      queryKey: ["care-episodes", value.episodeId],
    });
  };
  const start = useMutation({
    mutationFn: () => startCareVisit(id, value.visitVersion),
    onSuccess: refresh,
  });
  const save = useMutation({
    mutationFn: () =>
      aiJobId
        ? saveVisitReport(id, value.version, draft, aiJobId)
        : saveVisitReport(id, value.version, draft),
    onSuccess: refresh,
  });
  const finalize = useMutation({
    mutationFn: () => finalizeVisitReport(id, value.version),
    onSuccess: refresh,
  });
  const correct = useMutation({
    mutationFn: () => correctVisitReport(id, value.version, correctionReason),
    onSuccess: refresh,
  });
  const dirty =
    aiJobId !== value.aiJobId ||
    (Object.keys(fields) as (keyof ReportFields)[]).some(
      (field) => draft[field] !== (value[field] ?? ""),
    );
  const busy =
    start.isPending ||
    save.isPending ||
    finalize.isPending ||
    correct.isPending;
  const editable =
    canWrite &&
    value.status === "DRAFT" &&
    (value.visitStatus === "IN_PROGRESS" || value.visitStatus === "COMPLETED");
  return (
    <section>
      <h1>{fr ? "Consultation médicale" : "Medical consultation"}</h1>
      <Link to={`/care/episodes/${value.episodeId}`}>
        {fr ? "Voir la prise en charge" : "View care episode"}
      </Link>
      <p>
        {value.visitStatus} · {fr ? "Rapport" : "Report"} {value.status} ·{" "}
        {fr ? "Version" : "Revision"} {value.revision}
      </p>
      {canWrite && ["SCHEDULED", "CALLED"].includes(value.visitStatus) && (
        <button disabled={busy} onClick={() => start.mutate()}>
          {fr ? "Démarrer la rencontre médicale" : "Start medical encounter"}
        </button>
      )}
      {episode.data && (
        <p>
          {episode.data.startedAt
            ? `${fr ? "Début du suivi" : "Follow-up started"}: ${new Date(episode.data.startedAt).toLocaleString()} · ${fr ? "Expiration" : "Expires"}: ${episode.data.expiresAt ? new Date(episode.data.expiresAt).toLocaleString() : "—"}`
            : fr
              ? "La première rencontre démarrera la période de suivi."
              : "The first encounter starts the follow-up period."}
        </p>
      )}
      <h2>
        {fr ? "Dossier autorisé et accueil" : "Authorized record and intake"}
      </h2>
      {patient.isError && <ApiErrorMessage error={patient.error} />}
      {patient.data && (
        <>
          <Link to={`/patients/${value.patientId}`}>
            {fr ? "Ouvrir le dossier patient" : "Open patient record"} ·{" "}
            {patient.data.patientNumber}
          </Link>
          <p>
            {fr ? "Allergies" : "Allergies"}:{" "}
            {patient.data.allergies
              .map((a) => `${a.allergen}${a.reaction ? `: ${a.reaction}` : ""}`)
              .join(", ") || "—"}
          </p>
          <p>
            {fr ? "Antécédents" : "Medical history"}:{" "}
            {patient.data.medicalHistories.map((h) => h.condition).join(", ") ||
              "—"}
          </p>
          <p>
            {fr ? "Alertes" : "Flags"}:{" "}
            {patient.data.flags
              .filter((f) => f.active)
              .map((f) => f.label)
              .join(", ") || "—"}
          </p>
        </>
      )}
      {intake.data && (
        <>
          <p>{intake.data.observations}</p>
          <dl>
            {Object.entries(intake.data.vitals).map(([name, v]) => (
              <div key={name}>
                <dt>{intakeNames[name]?.[fr ? 0 : 1] ?? name}</dt>
                <dd>{v}</dd>
              </div>
            ))}
          </dl>
        </>
      )}
      {intake.error && <ApiErrorMessage error={intake.error} />}
      {episode.error && <ApiErrorMessage error={episode.error} />}
      <CarePatientHistory patientId={value.patientId} currentVisitId={id}/>
      <CareAiAssistant
        visitId={id}
        organizationId={value.organizationId}
        professionalId={value.professionalId}
        canWrite={canWrite}
        editable={editable}
        fr={fr}
        onApply={(proposal, jobId) => {
          setDraft(proposal);
          setAiJobId(jobId);
        }}
      />
      {aiJobId && (
        <p>
          {fr
            ? "Rapport issu d’une proposition IA, soumis à validation médicale."
            : "Report based on an AI proposal, subject to medical validation."}
        </p>
      )}
      <form
        className="entity-form"
        onSubmit={(event) => {
          event.preventDefault();
          save.mutate();
        }}
      >
        <h2>{fr ? "Rapport de cette visite" : "This visit’s report"}</h2>
        <fieldset disabled={!editable || busy}>
          {(Object.keys(fields) as (keyof ReportFields)[]).map((field) => (
            <label key={field}>
              {fields[field][fr ? 0 : 1]}
              <textarea
                maxLength={
                  field === "reason"
                    ? 10000
                    : field === "observations"
                      ? 50000
                      : 20000
                }
                value={draft[field]}
                onChange={(e) =>
                  setDraft({ ...draft, [field]: e.target.value })
                }
              />
            </label>
          ))}
          {editable && (
            <button disabled={!draft.reason.trim()}>
              {fr ? "Enregistrer le brouillon" : "Save draft"}
            </button>
          )}
        </fieldset>
      </form>
      {editable && (
        <button
          disabled={busy || dirty || !draft.diagnosis.trim()}
          onClick={() => finalize.mutate()}
        >
          {fr
            ? "Valider le rapport et terminer la visite"
            : "Validate report and complete visit"}
        </button>
      )}
      {dirty && (
        <p>
          {fr
            ? "Enregistrez les modifications avant de valider."
            : "Save changes before validation."}
        </p>
      )}
      {canWrite && value.status === "VALIDATED" && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            correct.mutate();
          }}
        >
          <label>
            {fr ? "Motif de correction du rapport" : "Report correction reason"}
            <input
              required
              maxLength={2000}
              value={correctionReason}
              onChange={(e) => setCorrectionReason(e.target.value)}
            />
          </label>
          <button disabled={busy || !correctionReason.trim()}>
            {fr
              ? "Corriger sans écraser la version validée"
              : "Correct while preserving the validated version"}
          </button>
        </form>
      )}
      {[start.error, save.error, finalize.error, correct.error]
        .filter(Boolean)
        .map((error, index) => (
          <ApiErrorMessage key={index} error={error} />
        ))}
      <details>
        <summary>
          {fr ? "Versions validées du rapport" : "Validated report revisions"}
        </summary>
        {value.revisions?.map((revision) => (
          <article key={revision.revision}>
            <h3>
              {fr ? "Version" : "Revision"} {revision.revision}
            </h3>
            <p>
              {revision.validatedAt &&
                new Date(revision.validatedAt).toLocaleString()}{" "}
              · {revision.correctionReason}
            </p>
            {revision.aiJobId && (
              <p>
                {fr
                  ? "Rapport préparé avec assistance IA et validé par le médecin."
                  : "Report prepared with AI assistance and validated by the doctor."}
              </p>
            )}
            {(Object.keys(fields) as (keyof ReportFields)[]).map((field) => (
              <p key={field}>
                <strong>{fields[field][fr ? 0 : 1]}: </strong>
                {revision[field]}
              </p>
            ))}
          </article>
        ))}
      </details>
      {(value.visitStatus === "IN_PROGRESS" ||
        value.visitStatus === "COMPLETED") && (
        <>
          <VisitPrescription id={id} fr={fr} canWrite={canWrite} />
          <VisitDocuments
            id={id}
            fr={fr}
            canGenerate={documentCreate && canWrite && value.status === "VALIDATED"}
          />
        </>
      )}
    </section>
  );
}
function VisitDocuments({
  id,
  fr,
  canGenerate,
}: {
  id: string;
  fr: boolean;
  canGenerate: boolean;
}) {
  const cache = useQueryClient();
  const docs = useQuery({
    queryKey: ["visit-documents", id],
    queryFn: () => listVisitDocuments(id),
  });
  const generate = useMutation({
    mutationFn: (type: VisitDocument["type"]) =>
      generateVisitDocument(id, type),
    onSuccess: () =>
      cache.invalidateQueries({ queryKey: ["visit-documents", id] }),
  });
  const download = useMutation({
    mutationFn: ({
      document,
      print,
    }: {
      document: VisitDocument;
      print: boolean;
    }) => downloadDocument(document, print),
  });
  return (
    <section>
      <h2>{fr ? "Documents PDF" : "PDF documents"}</h2>
      {canGenerate && (
        <div className="card-actions">
          <button
            disabled={generate.isPending}
            onClick={() => generate.mutate("REPORT")}
          >
            {fr ? "Générer le rapport PDF" : "Generate report PDF"}
          </button>
          <button
            disabled={generate.isPending}
            onClick={() => generate.mutate("PRESCRIPTION")}
          >
            {fr ? "Générer l’ordonnance PDF" : "Generate prescription PDF"}
          </button>
        </div>
      )}
      {docs.data?.map((document) => (
        <article key={document.id}>
          <p>
            {document.fileName} ·{" "}
            {new Date(document.createdAt).toLocaleString()}
          </p>
          <button
            disabled={download.isPending}
            onClick={() => download.mutate({ document, print: false })}
          >
            {fr ? "Télécharger" : "Download"}
          </button>
          <button
            disabled={download.isPending}
            onClick={() => download.mutate({ document, print: true })}
          >
            {fr ? "Imprimer" : "Print"}
          </button>
        </article>
      ))}
      {[docs.error, generate.error, download.error]
        .filter(Boolean)
        .map((error, index) => (
          <ApiErrorMessage key={index} error={error} />
        ))}
    </section>
  );
}
