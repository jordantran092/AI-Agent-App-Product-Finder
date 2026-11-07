export type AuthUser = { username: string };
export type Product = {
  id: number;
  name: string;
  brand: string;
  model: string;
  category: string;
  description: string;
  imageUrl: string | null;
  attributes: Record<string, unknown>;
};
export type SavedItem = {
  product: Product;
  note: string | null;
  trustLabel: string | null;
};
export type Offer = {
  retailer: string;
  price: number;
  condition: string;
};
export type ChatProduct = Omit<Product, "id"> & {
  productId: number;
  bestOffer: Offer;
  explanation: string;
};
export type Requirements = {
  category: string;
  maxPrice: number | null;
  mustHaves: string[];
  niceToHaves: string[];
  excludedBrands: string[];
  condition: string | null;
  attributes: Record<string, unknown>;
};
export type ChatResponse = {
  state: "GATHERING" | "CONFIRMING" | "SEARCHING" | "PRESENTING";
  history: { role: string; content: string; sentAt: string }[];
  requirements: Requirements | null;
  followUpQuestion: string | null;
  match: ChatProduct | null;
  noMatchMessage: string | null;
  constraintsToRelax: string[];
};
export type ProfileSettings = {
  budget: number | null;
  excludedBrands: string[];
  conditions: string[];
};
export type ProductPage = {
  discontinued: boolean;
  product: Product | null;
  note: string | null;
  trustLabel: string | null;
  savedOffer: Offer | null;
};
export type SharedProduct = {
  name: string;
  brand: string;
  model: string;
  imageUrl: string | null;
  retailer: string | null;
  price: number | null;
};
export type AddProductResult = {
  status: "ADDED" | "ALREADY_SAVED" | "SUGGESTION" | "NOT_FOUND";
  product: Product | null;
  trustLabel: string | null;
  message: string;
};

type CsrfResponse = { token: string; headerName: string };
type ApiErrorBody = { error?: string; fields?: Record<string, string> };

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const method = options.method ?? "GET";
  const headers = new Headers();
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (method !== "GET" && method !== "HEAD") {
    const csrf = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    if (!csrf.ok) throw new ApiError("Could not obtain a CSRF token", csrf.status);
    const csrfData = (await csrf.json()) as CsrfResponse;
    headers.set(csrfData.headerName, csrfData.token);
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  if (!response.ok) {
    const body = await response.text();
    let error: ApiErrorBody = {};
    if (body) {
      try {
        error = JSON.parse(body) as ApiErrorBody;
      } catch (problem) {
        if (!(problem instanceof SyntaxError)) throw problem;
      }
    }
    const details = error.fields ? Object.values(error.fields).join("; ") : "";
    throw new ApiError(
      [error.error ?? `Request failed (${response.status})`, details]
        .filter(Boolean)
        .join(": "),
      response.status,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  me: () => request<AuthUser>("/api/auth/me"),
  register: (username: string, password: string) =>
    request<AuthUser>("/api/auth/register", {
      method: "POST",
      body: { username, password },
    }),
  login: (username: string, password: string) =>
    request<AuthUser>("/api/auth/login", {
      method: "POST",
      body: { username, password },
    }),
  logout: () => request<void>("/api/auth/logout", { method: "POST" }),
  chat: () => request<ChatResponse>("/api/chat"),
  sendMessage: (text: string) =>
    request<ChatResponse>("/api/chat/messages", { method: "POST", body: { text } }),
  confirmRequirements: () =>
    request<ChatResponse>("/api/chat/confirm", { method: "POST" }),
  cancelRequirements: () =>
    request<ChatResponse>("/api/chat/cancel", { method: "POST" }),
  retryMatch: () => request<ChatResponse>("/api/chat/retry", { method: "POST" }),
  buyOrWait: (urgency: string) =>
    request<{ decision: string; reason: string; priceHistory: unknown }>(
      "/api/chat/buy-or-wait",
      { method: "POST", body: { urgency } },
    ),
  savedList: () => request<{ items: SavedItem[] }>("/api/saved-list"),
  addProduct: (productName: string) =>
    request<AddProductResult>("/api/saved-list/items", {
      method: "POST",
      body: { productName },
    }),
  saveRecommendation: (productId: number) =>
    request<AddProductResult>("/api/saved-list/recommendation", {
      method: "POST",
      body: { productId },
    }),
  confirmSuggestedProduct: (productId: number) =>
    request<AddProductResult>(`/api/saved-list/items/${productId}`, {
      method: "POST",
    }),
  removeProduct: (productId: number) =>
    request<void>(`/api/saved-list/items/${productId}`, { method: "DELETE" }),
  productPage: async (productId: number): Promise<ProductPage> => {
    try {
      return await request<ProductPage>(`/api/products/${productId}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 410) {
        return { discontinued: true, product: null, note: null, trustLabel: null, savedOffer: null };
      }
      throw error;
    }
  },
  bestPrice: (productId: number) =>
    request<{ discontinued: boolean; bestOffer: Offer | null }>(`/api/products/${productId}/best-price`, {
      method: "POST",
    }),
  saveNote: (productId: number, note: string) =>
    request<{ note: string | null; sanitized: boolean; message: string }>(
      `/api/saved-list/items/${productId}/note`,
      { method: "PUT", body: { note } },
    ),
  summarize: (productId: number) =>
    request<{ summary: string }>(`/api/products/${productId}/summary`, {
      method: "POST",
    }),
  profile: () => request<ProfileSettings>("/api/profile"),
  updateProfile: (settings: ProfileSettings) =>
    request<ProfileSettings>("/api/profile", { method: "PUT", body: settings }),
  shareList: () =>
    request<{ shareToken: string; shareUrl: string }>("/api/saved-list/share", {
      method: "POST",
    }),
  sharedList: (token: string) =>
    request<{ items: SharedProduct[] }>(`/api/shared-lists/${encodeURIComponent(token)}`),
};
