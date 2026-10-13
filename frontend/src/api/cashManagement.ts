import { apiRequest } from "./client";
import type { CashReceipt } from "./careBilling";
export type CashPeriod = { from: string; to: string; cashierPersonId?: string };
export type CashTotal = {
  currency: string;
  payments: number;
  refunds: number;
  net: number;
  count: number;
};
export type CashReport = CashPeriod & {
  totals: CashTotal[];
  cashiers: { cashierPersonId: string; totals: CashTotal[] }[];
};
export type CashClosure = CashPeriod & {
  id: string;
  number: string;
  createdAt: string;
  createdByPersonId: string;
  note?: string;
  amounts: {
    currency: string;
    payments: number;
    refunds: number;
    expectedNet: number;
    declaredAmount: number;
    difference: number;
  }[];
  receipts: CashReceipt[];
};
export const getCashReport = (period: CashPeriod) =>
  apiRequest<CashReport>(
    `/billing/care/cash-report?${new URLSearchParams(Object.entries(period).filter(([, v]) => !!v))}`,
  );
export const listCashClosures = () =>
  apiRequest<CashClosure[]>("/billing/care/cash-closures");
export const closeCash = (
  input: CashPeriod & {
    declaredAmounts: { currency: string; amount: number }[];
    note?: string;
  },
  key: string,
) =>
  apiRequest<CashClosure>("/billing/care/cash-closures", {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(input),
  });
