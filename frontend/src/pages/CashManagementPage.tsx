import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { ApiErrorMessage } from "../components/ApiErrorMessage";
import {
  closeCash,
  getCashReport,
  listCashClosures,
  type CashPeriod,
  type CashReport,
} from "../api/cashManagement";
export function CashManagementPage() {
  const { i18n } = useTranslation();
  const fr = i18n.language.startsWith("fr");
  const auth = useAuth();
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [cashier, setCashier] = useState("");
  const [period, setPeriod] = useState<CashPeriod>();
  const allowed = auth.hasAnyRole(
    "CASHIER",
    "ACCOUNTANT",
    "HOSPITAL_ADMIN",
    "PLATFORM_ADMIN",
  );
  const canClose = auth.hasAnyRole(
    "CASHIER",
    "HOSPITAL_ADMIN",
    "PLATFORM_ADMIN",
  );
  const canSelect = auth.hasAnyRole(
    "ACCOUNTANT",
    "HOSPITAL_ADMIN",
    "PLATFORM_ADMIN",
  );
  const report = useQuery({
    queryKey: ["cash-report", period],
    queryFn: () => getCashReport(period!),
    enabled: !!period && allowed,
  });
  const closures = useQuery({
    queryKey: ["cash-closures"],
    queryFn: listCashClosures,
    enabled: allowed,
  });
  if (!allowed)
    return (
      <p>
        {fr ? "Accès caisse non autorisé." : "Cash management access denied."}
      </p>
    );
  return (
    <section>
      <h1>
        {fr ? "Rapports et clôtures de caisse" : "Cash reports and closures"}
      </h1>
      <form
        className="entity-form"
        onSubmit={(event) => {
          event.preventDefault();
          setPeriod({
            from: new Date(from).toISOString(),
            to: new Date(to).toISOString(),
            ...(cashier ? { cashierPersonId: cashier } : {}),
          });
        }}
      >
        <div className="form-grid">
          <label>
            {fr ? "Début inclus" : "Start inclusive"}
            <input
              required
              type="datetime-local"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </label>
          <label>
            {fr ? "Fin exclue" : "End exclusive"}
            <input
              required
              type="datetime-local"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </label>
          {canSelect && (
            <label>
              {fr
                ? "Identifiant de l’agent (vide : tous)"
                : "Agent ID (empty: all)"}
              <input
                value={cashier}
                onChange={(e) => setCashier(e.target.value)}
              />
            </label>
          )}
        </div>
        <button disabled={!from || !to || new Date(from) >= new Date(to)}>
          {fr ? "Consulter la période" : "Load period"}
        </button>
      </form>
      {report.error && <ApiErrorMessage error={report.error} />}
      {report.data && (
        <>
          <CashTotals totals={report.data.totals} fr={fr} />
          {report.data.cashiers.map((agent) => (
            <details key={agent.cashierPersonId}>
              <summary>{agent.cashierPersonId}</summary>
              <CashTotals totals={agent.totals} fr={fr} />
            </details>
          ))}
          {canClose && !!report.data.cashierPersonId && (
            <ClosureForm
              key={JSON.stringify(period)}
              report={report.data}
              fr={fr}
            />
          )}
        </>
      )}
      {canClose && report.data && !report.data.cashierPersonId && (
        <p>
          {fr
            ? "Choisissez un agent pour clôturer sa caisse."
            : "Choose an agent to close their register."}
        </p>
      )}
      <h2>{fr ? "Clôtures enregistrées" : "Saved closures"}</h2>
      {closures.error && <ApiErrorMessage error={closures.error} />}{" "}
      {closures.data?.map((closure) => (
        <details key={closure.id}>
          <summary>
            {closure.number} · {new Date(closure.createdAt).toLocaleString()} ·{" "}
            {closure.cashierPersonId}
          </summary>
          <p>
            {closure.from} — {closure.to}
          </p>
          <p>{closure.note}</p>
          <table>
            <thead>
              <tr>
                <th>{fr ? "Devise" : "Currency"}</th>
                <th>{fr ? "Attendu" : "Expected"}</th>
                <th>{fr ? "Déclaré" : "Declared"}</th>
                <th>{fr ? "Écart" : "Difference"}</th>
              </tr>
            </thead>
            <tbody>
              {closure.amounts.map((a) => (
                <tr key={a.currency}>
                  <td>{a.currency}</td>
                  <td>{a.expectedNet}</td>
                  <td>{a.declaredAmount}</td>
                  <td>{a.difference}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <ul>
            {closure.receipts?.map((r) => (
              <li key={r.id}>
                {r.receiptNumber} · {r.operationType} · {r.amount} {r.currency}
              </li>
            ))}
          </ul>
        </details>
      ))}
    </section>
  );
}
function CashTotals({
  totals,
  fr,
}: {
  totals: CashReport["totals"];
  fr: boolean;
}) {
  return (
    <table>
      <thead>
        <tr>
          <th>{fr ? "Devise" : "Currency"}</th>
          <th>{fr ? "Encaissements" : "Payments"}</th>
          <th>{fr ? "Restitutions" : "Refunds"}</th>
          <th>Net</th>
          <th>{fr ? "Opérations" : "Operations"}</th>
        </tr>
      </thead>
      <tbody>
        {totals.map((t) => (
          <tr key={t.currency}>
            <td>{t.currency}</td>
            <td>{t.payments}</td>
            <td>{t.refunds}</td>
            <td>{t.net}</td>
            <td>{t.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
function ClosureForm({ report, fr }: { report: CashReport; fr: boolean }) {
  const cache = useQueryClient();
  const [values, setValues] = useState<Record<string, string>>({});
  const [note, setNote] = useState("");
  const key = useRef(crypto.randomUUID());
  const close = useMutation({
    mutationFn: () =>
      closeCash(
        {
          from: report.from,
          to: report.to,
          cashierPersonId: report.cashierPersonId,
          declaredAmounts: report.totals.map((t) => ({
            currency: t.currency,
            amount: Number(values[t.currency]),
          })),
          note,
        },
        key.current,
      ),
    onSuccess: () => cache.invalidateQueries({ queryKey: ["cash-closures"] }),
  });
  return (
    <form
      className="entity-form"
      onSubmit={(e) => {
        e.preventDefault();
        close.mutate();
      }}
    >
      <h2>{fr ? "Clôturer la période" : "Close period"}</h2>
      {report.totals.map((t) => (
        <label key={t.currency}>
          {fr ? "Montant cash compté" : "Counted cash"} ({t.currency})
          <input
            required
            type="number"
            min="0"
            step="0.01"
            value={values[t.currency] ?? ""}
            onChange={(e) => {
              key.current = crypto.randomUUID();
              setValues({ ...values, [t.currency]: e.target.value });
              close.reset();
            }}
          />
        </label>
      ))}
      <label>
        {fr ? "Note de clôture" : "Closure note"}
        <textarea
          value={note}
          onChange={(e) => {
            key.current = crypto.randomUUID();
            setNote(e.target.value);
            close.reset();
          }}
        />
      </label>
      {close.error && <ApiErrorMessage error={close.error} />}{" "}
      {close.isSuccess && (
        <p role="status">{fr ? "Clôture enregistrée" : "Closure saved"}</p>
      )}
      <button
        disabled={
          close.isPending ||
          close.isSuccess ||
          !report.totals.length ||
          report.totals.some(
            (t) =>
              values[t.currency] === undefined || values[t.currency] === "",
          )
        }
      >
        {fr ? "Confirmer la clôture" : "Confirm closure"}
      </button>
    </form>
  );
}
