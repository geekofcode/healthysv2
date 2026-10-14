import {useHospitalPermission} from '../auth/HospitalPermissions';
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorMessage } from "./ApiErrorMessage";
import {
  correctVisitPrescription,
  finalizeVisitPrescription,
  getVisitPrescription,
  listPrescriptionRevisions,
  saveVisitPrescription,
  type PrescriptionItem,
  type VisitPrescription as Prescription,
} from "../api/visitDocuments";
const blank: PrescriptionItem = {
  medication: "",
  dosage: "",
  route: "",
  frequency: "",
  duration: "",
  instructions: "",
};
const names: Record<keyof PrescriptionItem, [string, string]> = {
  medication: ["Médicament", "Medication"],
  dosage: ["Posologie", "Dosage"],
  route: ["Voie", "Route"],
  frequency: ["Fréquence", "Frequency"],
  duration: ["Durée", "Duration"],
  instructions: ["Instructions", "Instructions"],
};
export function VisitPrescription({
  id,
  fr,
  canWrite,
}: {
  id: string;
  fr: boolean;
  canWrite: boolean;
}) {
  const allowed=useHospitalPermission('prescriptions','UPDATE');
  const data = useQuery({
    queryKey: ["visit-prescription", id],
    queryFn: () => getVisitPrescription(id),
  });
  if (data.isPending) return <p>…</p>;
  if (data.isError) return <ApiErrorMessage error={data.error} />;
  return (
    <PrescriptionForm
      key={`${data.data.version}-${data.data.status}`}
      id={id}
      fr={fr}
      canWrite={canWrite&&allowed}
      value={data.data}
    />
  );
}
function PrescriptionForm({
  id,
  fr,
  canWrite,
  value,
}: {
  id: string;
  fr: boolean;
  canWrite: boolean;
  value: Prescription;
}) {
  const cache = useQueryClient();
  const [items, setItems] = useState(value.items);
  const [reason, setReason] = useState("");
  const refresh = () => {
    void cache.invalidateQueries({ queryKey: ["visit-prescription", id] });
    void cache.invalidateQueries({ queryKey: ["visit-documents", id] });
  };
  const save = useMutation({
    mutationFn: () =>
      saveVisitPrescription(id, { expectedVersion: value.version, items }),
    onSuccess: refresh,
  });
  const finalize = useMutation({
    mutationFn: () => finalizeVisitPrescription(id, value.version),
    onSuccess: refresh,
  });
  const correct = useMutation({
    mutationFn: () => correctVisitPrescription(id, value.version, reason),
    onSuccess: refresh,
  });
  const dirty = JSON.stringify(items) !== JSON.stringify(value.items);
  const busy = save.isPending || finalize.isPending || correct.isPending;
  const editable = canWrite && value.status === "DRAFT";
  const revisions = useQuery({
    queryKey: ["prescription-revisions", id, value.version],
    queryFn: () => listPrescriptionRevisions(id),
  });
  return (
    <section>
      <h2>
        {fr ? "Ordonnance" : "Prescription"} · {value.status}
      </h2>
      {items.map((item, index) => (
        <fieldset key={index} disabled={!editable || busy}>
          <legend>
            {fr ? "Médicament" : "Medication"} {index + 1}
          </legend>
          <div className="form-grid">
            {(Object.keys(names) as (keyof PrescriptionItem)[]).map((field) => (
              <label key={field}>
                {names[field][fr ? 0 : 1]}
                <input
                  maxLength={
                    field === "instructions"
                      ? 2000
                      : field === "route"
                        ? 150
                        : field === "medication" || field === "dosage"
                          ? 500
                          : 300
                  }
                  value={item[field] ?? ""}
                  onChange={(event) =>
                    setItems(
                      items.map((entry, i) =>
                        i === index
                          ? { ...entry, [field]: event.target.value }
                          : entry,
                      ),
                    )
                  }
                />
              </label>
            ))}
          </div>
          {editable && (
            <button
              onClick={() => setItems(items.filter((_, i) => i !== index))}
            >
              {fr ? "Retirer" : "Remove"}
            </button>
          )}
        </fieldset>
      ))}
      {editable && (
        <div className="card-actions">
          <button
            disabled={busy}
            onClick={() => setItems([...items, { ...blank }])}
          >
            {fr ? "Ajouter un médicament" : "Add medication"}
          </button>
          <button
            disabled={
              busy ||
              !items.length ||
              items.some((i) => !i.medication.trim() || !i.dosage.trim())
            }
            onClick={() => save.mutate()}
          >
            {fr ? "Enregistrer l’ordonnance" : "Save prescription"}
          </button>
          <button
            disabled={busy || dirty || !items.length}
            onClick={() => finalize.mutate()}
          >
            {fr ? "Valider l’ordonnance" : "Validate prescription"}
          </button>
        </div>
      )}
      {canWrite && value.status === "VALIDATED" && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            correct.mutate();
          }}
        >
          <label>
            {fr
              ? "Motif de correction de l’ordonnance"
              : "Prescription correction reason"}
            <input
              required
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <button disabled={busy || !reason.trim()}>
            {fr ? "Ouvrir une correction" : "Start correction"}
          </button>
        </form>
      )}
      {[save.error, finalize.error, correct.error]
        .filter(Boolean)
        .map((error, index) => (
          <ApiErrorMessage key={index} error={error} />
        ))}
      <details>
        <summary>
          {fr
            ? "Versions validées de l’ordonnance"
            : "Validated prescription revisions"}
        </summary>
        {revisions.data?.map((revision) => (
          <article key={revision.id}>
            <h3>
              {fr ? "Version" : "Revision"} {revision.revision}
            </h3>
            <p>
              {new Date(revision.validatedAt).toLocaleString()} ·{" "}
              {revision.correctionReason}
            </p>
            <ul>
              {revision.items.map((item, index) => (
                <li key={index}>
                  {item.medication} · {item.dosage} · {item.frequency} ·{" "}
                  {item.duration} · {item.instructions}
                </li>
              ))}
            </ul>
          </article>
        ))}
        {revisions.error && <ApiErrorMessage error={revisions.error} />}
      </details>
      {dirty && (
        <p>
          {fr
            ? "Enregistrez les modifications avant validation."
            : "Save changes before validation."}
        </p>
      )}
    </section>
  );
}
