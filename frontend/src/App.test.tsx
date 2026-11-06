import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { ApiError, api, type ChatResponse, type Product, type SavedItem } from "./api";

vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api")>();
  return {
    ...actual,
    api: {
      me: vi.fn(),
      register: vi.fn(),
      login: vi.fn(),
      logout: vi.fn(),
      chat: vi.fn(),
      sendMessage: vi.fn(),
      confirmRequirements: vi.fn(),
      cancelRequirements: vi.fn(),
      retryMatch: vi.fn(),
      buyOrWait: vi.fn(),
      savedList: vi.fn(),
      addProduct: vi.fn(),
      confirmSuggestedProduct: vi.fn(),
      removeProduct: vi.fn(),
      productPage: vi.fn(),
      saveNote: vi.fn(),
      summarize: vi.fn(),
      profile: vi.fn(),
      updateProfile: vi.fn(),
      shareList: vi.fn(),
      sharedList: vi.fn(),
    },
  };
});

const product: Product = {
  id: 15,
  name: "Orion 15",
  brand: "Orion",
  model: "OR-15-128",
  category: "SMARTPHONE",
  description: "A synthetic phone for local testing.",
  imageUrl: null,
  attributes: { storageGb: 128 },
};

const savedItem: SavedItem = {
  product,
  note: null,
  trustLabel: "Trustworthy Reviews",
};

const gatheringChat: ChatResponse = {
  state: "GATHERING",
  history: [],
  requirements: null,
  followUpQuestion: null,
  match: null,
  noMatchMessage: null,
  constraintsToRelax: [],
};

const confirmingChat: ChatResponse = {
  ...gatheringChat,
  state: "CONFIRMING",
  history: [
    { role: "USER", content: "A phone under $700", sentAt: "2026-01-01T10:00:00Z" },
    { role: "ASSISTANT", content: "I understood smartphone under $700.", sentAt: "2026-01-01T10:00:01Z" },
  ],
  requirements: {
    category: "SMARTPHONE",
    maxPrice: 700,
    mustHaves: ["good camera"],
    niceToHaves: ["long battery life"],
    excludedBrands: [],
    condition: "NEW",
    attributes: { storageGb: 128 },
  },
};

function signedIn() {
  vi.mocked(api.me).mockResolvedValue({ username: "test-user" });
}

function renderSignedIn() {
  signedIn();
  return render(<App />);
}

beforeEach(() => {
  vi.clearAllMocks();
  window.history.replaceState({}, "", "/");
  vi.mocked(api.me).mockRejectedValue(new ApiError("Authentication is required", 401));
  vi.mocked(api.logout).mockResolvedValue(undefined);
  vi.mocked(api.chat).mockResolvedValue(gatheringChat);
  vi.mocked(api.savedList).mockResolvedValue({ items: [] });
  vi.mocked(api.profile).mockResolvedValue({
    budget: null,
    excludedBrands: [],
    conditions: [],
  });
});

afterEach(() => cleanup());

describe("Product Finder user flows", () => {
  it("registers, signs out, and signs back in", async () => {
    const user = userEvent.setup();
    vi.mocked(api.register).mockResolvedValue({ username: "new-user" });
    vi.mocked(api.login).mockResolvedValue({ username: "new-user" });
    vi.mocked(api.chat).mockResolvedValue(gatheringChat);

    render(<App />);
    await user.click(await screen.findByRole("button", { name: "Create an account" }));
    await user.type(screen.getByLabelText("Username"), "new-user");
    await user.type(screen.getByLabelText(/Password/), "a-long-local-password");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("What are you looking for?")).toBeInTheDocument();
    expect(api.register).toHaveBeenCalledWith("new-user", "a-long-local-password");
    expect(api.login).toHaveBeenCalledWith("new-user", "a-long-local-password");

    await user.click(screen.getByRole("button", { name: "Log out" }));
    expect(api.logout).toHaveBeenCalledOnce();
    await user.type(await screen.findByLabelText("Username"), "new-user");
    await user.type(screen.getByLabelText(/Password/), "a-long-local-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("What are you looking for?")).toBeInTheDocument();
  });

  it("shows a vague-request follow-up and searches only after explicit confirmation", async () => {
    const user = userEvent.setup();
    vi.mocked(api.chat).mockResolvedValue(gatheringChat);
    vi.mocked(api.sendMessage).mockResolvedValue({
      ...gatheringChat,
      history: [{ role: "ASSISTANT", content: "What kind of product do you need?", sentAt: "2026-01-01T10:00:00Z" }],
      followUpQuestion: "What kind of product do you need?",
    });
    vi.mocked(api.confirmRequirements).mockResolvedValue({
      ...confirmingChat,
      state: "PRESENTING",
    });

    renderSignedIn();
    await screen.findByText("What are you looking for?");
    await user.type(screen.getByLabelText("Your product requirements"), "Something useful");
    await user.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByText("What kind of product do you need?", { selector: ".callout" })).toBeInTheDocument();
    expect(api.confirmRequirements).not.toHaveBeenCalled();

    vi.mocked(api.sendMessage).mockResolvedValue(confirmingChat);
    await user.type(screen.getByLabelText("Your product requirements"), "A phone under $700");
    await user.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByText("Does this look right?")).toBeInTheDocument();
    expect(screen.getByText("Storage")).toBeInTheDocument();
    expect(screen.getByRole("listitem").textContent).toContain("Storage: 128 GB");
    expect(screen.queryByText('{"storageGb":128}')).not.toBeInTheDocument();
    expect(api.confirmRequirements).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Confirm and search" }));
    await waitFor(() => expect(api.confirmRequirements).toHaveBeenCalledOnce());
  });

  it("updates profile filters and reports invalid settings", async () => {
    const user = userEvent.setup();
    vi.mocked(api.profile).mockResolvedValue({
      budget: 800,
      excludedBrands: ["Orion"],
      conditions: ["NEW"],
    });
    vi.mocked(api.updateProfile).mockRejectedValue(
      new ApiError("Invalid profile", 400),
    );
    renderSignedIn();
    await screen.findByText("What are you looking for?");
    await user.click(screen.getByRole("button", { name: "Profile settings" }));
    const budget = await screen.findByRole("spinbutton");
    await user.clear(budget);
    await user.type(budget, "650");
    const brands = screen.getByLabelText(/Excluded brands/);
    await user.clear(brands);
    await user.type(brands, "Orion, Maple");
    await user.click(screen.getByRole("button", { name: "Save preferences" }));

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({
      budget: 650,
      excludedBrands: ["Orion", "Maple"],
      conditions: ["NEW"],
    }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid profile");
  });

  it("explains when there are no eligible candidates and surfaces a temporary agent error", async () => {
    const user = userEvent.setup();
    vi.mocked(api.chat).mockResolvedValue({
      ...gatheringChat,
      noMatchMessage: "No products meet the selected filters.",
      constraintsToRelax: ["product specifications", "maximum price"],
    });
    vi.mocked(api.sendMessage).mockRejectedValue(
      new ApiError("DeepSeek is temporarily unavailable", 503),
    );
    renderSignedIn();
    expect(await screen.findByText("No products meet the selected filters.")).toBeInTheDocument();
    expect(screen.getByText(/Consider relaxing: product specifications, maximum price/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retry search" })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Your product requirements"), "Relax the price");
    await user.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("DeepSeek is temporarily unavailable");
  });

  it("uses buy/wait advice when history is missing", async () => {
    const user = userEvent.setup();
    vi.mocked(api.chat).mockResolvedValue({
      ...gatheringChat,
      state: "PRESENTING",
      match: {
        productId: product.id,
        name: product.name,
        brand: product.brand,
        model: product.model,
        category: product.category,
        description: product.description,
        imageUrl: null,
        attributes: product.attributes,
        bestOffer: { retailer: "Maple Market", price: 679.99, condition: "NEW" },
        explanation: "This product fits the key requirements.",
      },
    });
    vi.mocked(api.buyOrWait).mockResolvedValue({
      decision: "WAIT",
      reason: "There is no history; urgency supports waiting.",
      priceHistory: null,
    });
    renderSignedIn();
    await screen.findByText("BEST MATCH");
    await user.click(screen.getByRole("button", { name: "Buy now or wait?" }));
    const advice = await screen.findByText(
      (_, element) => Boolean(element?.classList.contains("callout")
        && element.textContent?.includes("There is no history; urgency supports waiting.")),
    );
    expect(advice).toHaveTextContent("WAIT");
    expect(api.buyOrWait).toHaveBeenCalledWith("I need it soon");
  });

  it("asks before adding a similar model, displays trust, and confirms removal", async () => {
    const user = userEvent.setup();
    const savedAfterAdd: SavedItem = { ...savedItem, note: "Compare this one" };
    vi.mocked(api.savedList)
      .mockResolvedValueOnce({ items: [] })
      .mockResolvedValueOnce({ items: [savedAfterAdd] })
      .mockResolvedValueOnce({ items: [] });
    vi.mocked(api.addProduct).mockResolvedValue({
      status: "SUGGESTION",
      product,
      trustLabel: null,
      message: "Did you mean this product? Confirm to add it",
    });
    vi.mocked(api.confirmSuggestedProduct).mockResolvedValue({
      status: "ADDED",
      product,
      trustLabel: "Trustworthy Reviews",
      message: "Product added to your saved list",
    });
    vi.mocked(api.removeProduct).mockResolvedValue(undefined);
    vi.spyOn(window, "confirm").mockReturnValue(true);

    renderSignedIn();
    await screen.findByText("What are you looking for?");
    await user.click(screen.getByRole("button", { name: "Saved list" }));
    await user.type(await screen.findByLabelText("Add a product by model"), "OR-15-12");
    await user.click(screen.getByRole("button", { name: "Add product" }));
    expect(await screen.findByText("DID YOU MEAN?")).toBeInTheDocument();
    expect(api.confirmSuggestedProduct).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Yes, add it" }));
    expect(await screen.findByText("Trustworthy Reviews")).toBeInTheDocument();
    expect(api.confirmSuggestedProduct).toHaveBeenCalledWith(product.id);

    const card = screen.getByRole("article");
    await user.click(within(card).getByRole("button", { name: "Remove" }));
    expect(api.removeProduct).toHaveBeenCalledWith(product.id);
    expect(await screen.findByText("Your list is empty")).toBeInTheDocument();
  });

  it("does not add a rejected suggestion and prevents sharing an empty list", async () => {
    const user = userEvent.setup();
    vi.mocked(api.savedList).mockResolvedValue({ items: [] });
    vi.mocked(api.addProduct).mockResolvedValue({
      status: "SUGGESTION",
      product,
      trustLabel: null,
      message: "Did you mean this product? Confirm to add it",
    });
    renderSignedIn();
    await screen.findByText("What are you looking for?");
    await user.click(screen.getByRole("button", { name: "Saved list" }));
    await user.type(await screen.findByLabelText("Add a product by model"), "OR-15-12");
    await user.click(screen.getByRole("button", { name: "Add product" }));
    await screen.findByText("DID YOU MEAN?");
    await user.click(screen.getByRole("button", { name: "No" }));

    expect(api.confirmSuggestedProduct).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Share list" })).toBeDisabled();
    expect(screen.getByText("Your list is empty")).toBeInTheDocument();
  });

  it("shows product review summary and saves personal notes", async () => {
    const user = userEvent.setup();
    vi.mocked(api.savedList).mockResolvedValue({ items: [savedItem] });
    vi.mocked(api.productPage).mockResolvedValue({
      discontinued: false,
      product,
      note: "Check battery",
      trustLabel: "Trustworthy Reviews",
      bestOffer: { retailer: "Maple Market", price: 679.99, condition: "NEW" },
    });
    vi.mocked(api.summarize).mockResolvedValue({ summary: "Reviewers praise the display." });
    vi.mocked(api.saveNote).mockResolvedValue({
      note: "Check battery before buying",
      sanitized: false,
      message: "Note saved",
    });

    renderSignedIn();
    await screen.findByText("What are you looking for?");
    await user.click(screen.getByRole("button", { name: "Saved list" }));
    await user.click(await screen.findByRole("button", { name: "View details" }));
    expect(await screen.findByText("Review trust: Trustworthy Reviews")).toBeInTheDocument();
    expect(screen.getByText(/Maple Market · .*679\.99 \(New\)/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Summarize" }));
    expect(await screen.findByText("Reviewers praise the display.")).toBeInTheDocument();
    const note = screen.getByLabelText("Your note");
    await user.clear(note);
    await user.type(note, "Check battery before buying");
    await user.click(screen.getByRole("button", { name: "Save note" }));
    await waitFor(() => expect(api.saveNote).toHaveBeenCalledWith(product.id, "Check battery before buying"));
  });

  it("copies a share link and renders a shared list without authentication", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    vi.mocked(api.savedList).mockResolvedValue({ items: [savedItem] });
    vi.mocked(api.shareList).mockResolvedValue({
      shareToken: "a-secure-token",
      shareUrl: "http://localhost:5173/shared/a-secure-token",
    });
    renderSignedIn();
    await screen.findByText("What are you looking for?");
    await user.click(screen.getByRole("button", { name: "Saved list" }));
    await user.click(await screen.findByRole("button", { name: "Share list" }));
    expect(await screen.findByText("Share link copied to the clipboard.")).toBeInTheDocument();
    expect(writeText).toHaveBeenCalledWith("http://localhost:5173/shared/a-secure-token");

    cleanup();
    window.history.replaceState({}, "", "/shared/a-secure-token");
    vi.mocked(api.me).mockClear();
    vi.mocked(api.sharedList).mockResolvedValue({
      items: [{
        name: product.name,
        brand: product.brand,
        model: product.model,
        imageUrl: null,
        retailer: "Maple Market",
        price: 679.99,
      }],
    });
    render(<App />);
    expect(await screen.findByText("Shared list · read only")).toBeInTheDocument();
    expect(await screen.findByText("Orion 15")).toBeInTheDocument();
    expect(api.me).not.toHaveBeenCalled();
    expect(api.sharedList).toHaveBeenCalledWith("a-secure-token");
  });

  it("shows an explicit message for an invalid shared-list token", async () => {
    window.history.replaceState({}, "", "/shared/invalid-token");
    vi.mocked(api.sharedList).mockRejectedValue(new ApiError("This list is no longer available", 404));
    render(<App />);
    expect(await screen.findByText("This list is unavailable")).toBeInTheDocument();
    expect(screen.getByText("This list is no longer available")).toBeInTheDocument();
    expect(api.me).not.toHaveBeenCalled();
  });
});
