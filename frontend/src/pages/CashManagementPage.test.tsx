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
const mock = vi.hoisted(() => ({ close: vi.fn().mockResolvedValue({}) }));
vi.mock("react-i18next", () => ({
  useTranslation: () => ({ i18n: { language: "fr" } }),
}));
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    hasAnyRole: (...roles: string[]) => roles.includes("CASHIER"),
  }),
}));
vi.mock("../api/client", () => ({ ApiError: class extends Error {} }));
vi.mock("../api/cashManagement", () => ({
  getCashReport: vi.fn().mockResolvedValue({
    from: "2026-10-10T10:00:00Z",
    to: "2026-10-10T12:00:00Z",
    cashierPersonId: "cashier",
    totals: [
      { currency: "CAD", payments: 20, refunds: 5, net: 15, count: 2 },
      { currency: "EUR", payments: 9, refunds: 0, net: 9, count: 1 },
    ],
    cashiers: [],
  }),
  listCashClosures: vi.fn().mockResolvedValue([]),
  closeCash: mock.close,
}));
import { CashManagementPage } from "./CashManagementPage";
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
it("requires counted cash per currency and locks a successful closure against duplicate submission", async () => {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <CashManagementPage />
    </QueryClientProvider>,
  );
  fireEvent.change(screen.getByLabelText("Début inclus"), {
    target: { value: "2026-10-10T10:00" },
  });
  fireEvent.change(screen.getByLabelText("Fin exclue"), {
    target: { value: "2026-10-10T12:00" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Consulter la période" }));
  const cad = await screen.findByLabelText("Montant cash compté (CAD)");
  expect(
    (
      screen.getByRole("button", {
        name: "Confirmer la clôture",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.change(cad, { target: { value: "15" } });
  fireEvent.change(screen.getByLabelText("Montant cash compté (EUR)"), {
    target: { value: "9" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Confirmer la clôture" }));
  await waitFor(() =>
    expect(mock.close).toHaveBeenCalledWith(
      expect.objectContaining({
        cashierPersonId: "cashier",
        declaredAmounts: [
          { currency: "CAD", amount: 15 },
          { currency: "EUR", amount: 9 },
        ],
      }),
      expect.any(String),
    ),
  );
  await screen.findByText("Clôture enregistrée");
  expect(
    (
      screen.getByRole("button", {
        name: "Confirmer la clôture",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
});
