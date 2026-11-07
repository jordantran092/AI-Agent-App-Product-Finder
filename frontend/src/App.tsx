import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  api,
  ApiError,
  type AddProductResult,
  type AuthUser,
  type ChatResponse,
  type Product,
  type ProductPage as ProductPageData,
  type Offer,
  type SavedItem,
  type SharedProduct,
} from "./api";

type View = "discover" | "saved" | "settings";
type SessionState = "loading" | "signedOut" | "signedIn";
const CONDITIONS = ["NEW", "REFURBISHED", "USED"];

function sharedTokenFromPath(path: string): string | null {
  const match = path.match(/^\/shared\/([^/]+)\/?$/);
  return match ? match[1] : null;
}

export default function App() {
  const [path, setPath] = useState(window.location.pathname);
  const [session, setSession] = useState<SessionState>("loading");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [view, setView] = useState<View>("discover");
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [pageError, setPageError] = useState("");

  useEffect(() => {
    const handlePopState = () => {
      setPath(window.location.pathname);
      setSelectedProductId(null);
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  useEffect(() => {
    if (sharedTokenFromPath(path)) return;
    let active = true;
    api.me().then((currentUser) => {
      if (!active) return;
      setUser(currentUser);
      setSession("signedIn");
    }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiError && error.status === 401) {
        setSession("signedOut");
      } else {
        setPageError(messageFor(error));
        setSession("signedOut");
      }
    });
    return () => {
      active = false;
    };
  }, [path]);

  const navigate = (nextPath: string) => {
    window.history.pushState({}, "", nextPath);
    setPath(window.location.pathname);
    setSelectedProductId(null);
    setPageError("");
  };

  const onAuthenticated = (currentUser: AuthUser) => {
    setUser(currentUser);
    setSession("signedIn");
    setPageError("");
  };

  const signOut = async () => {
    try {
      await api.logout();
      setUser(null);
      setSession("signedOut");
      setSelectedProductId(null);
      setView("discover");
    } catch (error) {
      setPageError(messageFor(error));
    }
  };

  const shareToken = sharedTokenFromPath(path);
  if (shareToken) return <SharedPage token={shareToken} onHome={() => navigate("/")} />;
  if (session === "loading") {
    return <main className="centered-page"><p role="status">Connecting to Product Finder…</p></main>;
  }
  if (session === "signedOut" || !user) {
    return (
      <AuthPage
        error={pageError}
        onError={setPageError}
        onAuthenticated={onAuthenticated}
      />
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand brand-button" onClick={() => {
          setSelectedProductId(null);
          setView("discover");
        }} aria-label="Product Finder home">
          <span className="brand-mark" aria-hidden="true">PF</span>
          <span>Product Finder</span>
        </button>
        <div className="account">
          <span>{user.username}</span>
          <button className="button button-quiet" onClick={signOut}>Log out</button>
        </div>
      </header>

      <nav className="primary-nav" aria-label="Main navigation">
        <button className={view === "discover" && selectedProductId === null ? "active" : ""}
          onClick={() => { setSelectedProductId(null); setView("discover"); }}>Discover</button>
        <button className={view === "saved" || selectedProductId !== null ? "active" : ""}
          onClick={() => { setSelectedProductId(null); setView("saved"); }}>Saved list</button>
        <button className={view === "settings" ? "active" : ""}
          onClick={() => { setSelectedProductId(null); setView("settings"); }}>Profile settings</button>
      </nav>

      {pageError && <ErrorBanner message={pageError} onDismiss={() => setPageError("")} />}

      {selectedProductId !== null ? (
        <ProductPage
          key={selectedProductId}
          productId={selectedProductId}
          onBack={() => setSelectedProductId(null)}
          onRemoved={() => setSelectedProductId(null)}
          onError={setPageError}
        />
      ) : view === "discover" ? (
        <ChatWorkspace
          onError={setPageError}
          onSaved={() => { setView("saved"); setPageError(""); }}
        />
      ) : view === "saved" ? (
        <SavedList
          onOpenProduct={setSelectedProductId}
          onError={setPageError}
        />
      ) : (
        <ProfileEditor onError={setPageError} />
      )}
    </main>
  );
}

function AuthPage(props: {
  error: string;
  onError: (message: string) => void;
  onAuthenticated: (user: AuthUser) => void;
}) {
  const [registering, setRegistering] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    props.onError("");
    try {
      const currentUser = registering
        ? await api.register(username, password)
        : await api.login(username, password);
      if (registering) {
        await api.login(username, password);
      }
      props.onAuthenticated(currentUser);
    } catch (error) {
      props.onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-layout">
      <section className="auth-aside">
        <a className="brand" href="/">
          <span className="brand-mark" aria-hidden="true">PF</span>
          <span>Product Finder</span>
        </a>
        <p className="eyebrow">SHOP WITH CLARITY</p>
        <h1>Find the right product for you.</h1>
        <p>Compare products and retailer prices, guided by what matters to you.</p>
      </section>
      <section className="auth-card" aria-labelledby="auth-heading">
        <p className="eyebrow">YOUR LOCAL PRODUCT ASSISTANT</p>
        <h2 id="auth-heading">{registering ? "Create your account" : "Welcome back"}</h2>
        <p className="muted">Sign in to search, save products, and manage your preferences.</p>
        {props.error && <ErrorBanner message={props.error} onDismiss={() => props.onError("")} />}
        <form className="stack" onSubmit={submit}>
          <label>
            Username
            <input autoComplete="username" required minLength={3} maxLength={80}
              value={username} onChange={(event) => setUsername(event.target.value)} />
          </label>
          <label>
            Password
            <input autoComplete={registering ? "new-password" : "current-password"}
              type="password" required minLength={registering ? 12 : 1}
              maxLength={72} value={password}
              onChange={(event) => setPassword(event.target.value)} />
            {registering && <span className="field-hint">Use at least 12 characters.</span>}
          </label>
          <button className="button button-primary" disabled={busy}>
            {busy ? "Please wait…" : registering ? "Create account" : "Sign in"}
          </button>
        </form>
        <p className="auth-switch">
          {registering ? "Already have an account?" : "New to Product Finder?"}{" "}
          <button className="text-button" onClick={() => {
            setRegistering((wasRegistering) => !wasRegistering);
            props.onError("");
          }}>
            {registering ? "Sign in" : "Create an account"}
          </button>
        </p>
      </section>
    </main>
  );
}

function ChatWorkspace({
  onError,
  onSaved,
}: {
  onError: (message: string) => void;
  onSaved: () => void;
}) {
  const [chat, setChat] = useState<ChatResponse | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const sentMessageRef = useRef(false);
  const [urgency, setUrgency] = useState("I need it soon");
  const [advice, setAdvice] = useState<{ decision: string; reason: string } | null>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    api.chat().then((response) => {
      if (active && !sentMessageRef.current) setChat(response);
    }).catch((error: unknown) => {
      if (active) onError(messageFor(error));
    });
    return () => { active = false; };
  }, [onError]);

  const run = async (action: () => Promise<ChatResponse>) => {
    setBusy(true);
    onError("");
    setNotice("");
    setAdvice(null);
    try {
      setChat(await action());
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const send = async (event: FormEvent) => {
    event.preventDefault();
    if (!text.trim()) return;
    const message = text.trim();
    setText("");
    sentMessageRef.current = true;
    setChat((current) => ({
      ...(current ?? {
        state: "GATHERING",
        history: [],
        requirements: null,
        followUpQuestion: null,
        match: null,
        noMatchMessage: null,
        constraintsToRelax: [],
      }),
      history: [
        ...(current?.history ?? []),
        { role: "USER", content: message, sentAt: new Date().toISOString() },
      ],
    }));
    await run(() => api.sendMessage(message));
  };

  const saveMatch = async (product: NonNullable<ChatResponse["match"]>) => {
    setBusy(true);
    try {
      const result = await api.saveRecommendation(product.productId);
      setNotice(result.message);
      if (result.status === "ADDED" || result.status === "ALREADY_SAVED") onSaved();
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const requestAdvice = async () => {
    setBusy(true);
    onError("");
    try {
      const result = await api.buyOrWait(urgency);
      setAdvice({ decision: result.decision, reason: result.reason });
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page-section">
      <div className="page-heading">
        <div>
          <p className="eyebrow">AI PRODUCT SEARCH</p>
          <h1>What are you looking for?</h1>
          <p className="muted">Describe your needs. Review the agent’s understanding before it searches.</p>
        </div>
      </div>

      <div className="workspace-grid">
        <section className="card chat-card" aria-label="Product search chat">
          <div className="chat-history" aria-live="polite">
            {chat?.history.length ? chat.history.map((message, index) => (
              <article className={`chat-message ${message.role.toLowerCase()}`} key={`${message.sentAt}-${index}`}>
                <span className="message-author">{message.role === "USER" ? "You" : "Product Finder"}</span>
                <p>{message.role === "ASSISTANT"
                  && message.content.startsWith("Please confirm these requirements:")
                  ? "Please confirm the requirements shown."
                  : message.content}</p>
              </article>
            )) : <p className="empty-state">Start with a product category, what matters to you, and your budget.</p>}
          </div>
          <form className="composer" onSubmit={send}>
            <label className="visually-hidden" htmlFor="chat-message">Your product requirements</label>
            <textarea id="chat-message" rows={3} maxLength={4000} value={text}
              onChange={(event) => setText(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) return;
                event.preventDefault();
                if (!busy && text.trim()) event.currentTarget.form?.requestSubmit();
              }}
              placeholder="For example: a phone under $700 with a good camera and at least 256 GB storage" />
            <button className="button button-primary" disabled={busy || !text.trim()}>
              {busy ? "Working…" : "Send"}
            </button>
          </form>
        </section>

        <aside className="stack">
          {chat?.requirements && (
            <section className="card">
              <p className="eyebrow">REQUIREMENTS TO CONFIRM</p>
              <h2>Does this look right?</h2>
              <RequirementsSummary requirements={chat.requirements} />
              {chat.state === "CONFIRMING" && (
                <div className="button-row">
                  <button className="button button-primary" disabled={busy}
                    onClick={() => void run(api.confirmRequirements)}>Confirm and search</button>
                  <button className="button button-secondary" disabled={busy}
                    onClick={() => void run(api.cancelRequirements)}>Cancel</button>
                </div>
              )}
            </section>
          )}
          {chat?.match && (
            <section className="card result-card">
              <p className="eyebrow">BEST MATCH</p>
              <ProductSummary product={chat.match} />
              <div className="offer-line">
                <span>Best offer</span>
                <strong>{chat.match.bestOffer.retailer} · {formatPrice(chat.match.bestOffer.price)}</strong>
              </div>
              <p>{chat.match.explanation}</p>
              <div className="button-row">
                <button className="button button-primary" disabled={busy}
                  onClick={() => void saveMatch(chat.match!)}>Save product</button>
                <label className="urgency-control">
                  Need
                  <select value={urgency} onChange={(event) => setUrgency(event.target.value)}>
                    <option>I need it now</option>
                    <option>I need it soon</option>
                    <option>I can wait for a better price</option>
                  </select>
                </label>
                <button className="button button-secondary" disabled={busy}
                  onClick={() => void requestAdvice()}>Buy now or wait?</button>
              </div>
              {advice && <p className="callout"><strong>{advice.decision.replaceAll("_", " ")}</strong> — {advice.reason}</p>}
            </section>
          )}
          {chat?.noMatchMessage && (
            <section className="card">
              <p>{chat.noMatchMessage}</p>
              {chat.constraintsToRelax.length > 0 && (
                <p className="muted">Consider relaxing: {chat.constraintsToRelax.join(", ")}</p>
              )}
              <p className="muted">Update your request in the chat to change these constraints.</p>
            </section>
          )}
          {notice && <p role="status" className="success-banner">{notice}</p>}
        </aside>
      </div>
    </section>
  );
}

function RequirementsSummary({ requirements }: { requirements: NonNullable<ChatResponse["requirements"]> }) {
  return (
    <dl className="requirements-list">
      <div><dt>Category</dt><dd>{humanize(requirements.category)}</dd></div>
      {requirements.maxPrice !== null && <div><dt>Maximum price</dt><dd>{formatPrice(requirements.maxPrice)}</dd></div>}
      {requirements.condition && <div><dt>Condition</dt><dd>{humanize(requirements.condition)}</dd></div>}
      <div><dt>Must have</dt><dd>{requirements.mustHaves.join(", ") || "No specific requirements"}</dd></div>
      <div><dt>Nice to have</dt><dd>{requirements.niceToHaves.join(", ") || "None"}</dd></div>
      <div><dt>Excluded brands</dt><dd>{requirements.excludedBrands.join(", ") || "None"}</dd></div>
      {Object.keys(requirements.attributes).length > 0 && (
        <div>
          <dt>Category-specific details</dt>
          <dd><ul className="attribute-list">
            {Object.entries(requirements.attributes).map(([key, value]) => (
              <li key={key}><strong>{formatAttributeLabel(key)}</strong>: {formatAttributeValue(value)}</li>
            ))}
          </ul></dd>
        </div>
      )}
    </dl>
  );
}

function SavedList({
  onOpenProduct,
  onError,
}: {
  onOpenProduct: (id: number) => void;
  onError: (message: string) => void;
}) {
  const [items, setItems] = useState<SavedItem[]>([]);
  const [productName, setProductName] = useState("");
  const [suggestion, setSuggestion] = useState<AddProductResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [shareUrl, setShareUrl] = useState("");

  const refresh = async () => {
    const result = await api.savedList();
    setItems(result.items);
  };
  useEffect(() => {
    let active = true;
    api.savedList().then((result) => {
      if (active) setItems(result.items);
    }).catch((error: unknown) => {
      if (active) onError(messageFor(error));
    });
    return () => { active = false; };
  }, [onError]);

  const add = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setNotice("");
    setSuggestion(null);
    try {
      const result = await api.addProduct(productName.trim());
      setNotice(result.message);
      setProductName("");
      if (result.status === "SUGGESTION") setSuggestion(result);
      else if (result.status === "ADDED" || result.status === "ALREADY_SAVED") await refresh();
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const confirmSuggestion = async () => {
    if (!suggestion?.product) return;
    setBusy(true);
    try {
      const result = await api.confirmSuggestedProduct(suggestion.product.id);
      setNotice(result.message);
      setSuggestion(null);
      await refresh();
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (item: SavedItem) => {
    if (!window.confirm(`Remove ${item.product.name} from your saved list?`)) return;
    setBusy(true);
    try {
      await api.removeProduct(item.product.id);
      await refresh();
      setNotice(`${item.product.name} was removed.`);
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const createShareLink = async () => {
    setBusy(true);
    setNotice("");
    try {
      const result = await api.shareList();
      setShareUrl(result.shareUrl);
      try {
        await navigator.clipboard.writeText(result.shareUrl);
        setNotice("Share link copied to the clipboard.");
      } catch {
        setNotice("Share link created. Clipboard access is unavailable; copy the link below.");
      }
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page-section">
      <div className="page-heading">
        <div>
          <p className="eyebrow">YOUR COLLECTION</p>
          <h1>Saved products</h1>
          <p className="muted">Track products, review insights, and retailer offers in one place.</p>
        </div>
        <button className="button button-secondary" disabled={busy || items.length === 0}
          onClick={() => void createShareLink()}>Share list</button>
      </div>
      <form className="add-product-form card" onSubmit={add}>
        <label htmlFor="product-name">Add a product</label>
        <div className="inline-form">
          <input id="product-name" value={productName} maxLength={200}
            onChange={(event) => setProductName(event.target.value)}
            placeholder="Enter a product model name" required />
          <button className="button button-primary" disabled={busy || !productName.trim()}>Find product</button>
        </div>
      </form>
      {notice && <p role="status" className="success-banner">{notice}</p>}
      {shareUrl && (
        <div className="share-callout">
          <a href={shareUrl}>{shareUrl}</a>
          <span>Anyone with this link can view the list.</span>
        </div>
      )}
      {suggestion?.product && (
        <section className="suggestion-card card" aria-label="Suggested product">
          <div><p className="eyebrow">DID YOU MEAN?</p><ProductSummary product={suggestion.product} /></div>
          <div className="button-row">
            <button className="button button-primary" disabled={busy}
              onClick={() => void confirmSuggestion()}>Yes, add it</button>
            <button className="button button-secondary" onClick={() => setSuggestion(null)}>No</button>
          </div>
        </section>
      )}
      {items.length === 0 ? (
        <div className="empty-panel"><h2>Your list is empty</h2><p>Find a product by name to start tracking it.</p></div>
      ) : (
        <div className="saved-grid">
          {items.map((item) => (
            <article className="card saved-card" key={item.product.id}>
              <button className="product-card-button" onClick={() => onOpenProduct(item.product.id)}>
                <ProductSummary product={item.product} />
              </button>
              <div className="trust-label">{item.trustLabel ?? "Review analysis unavailable"}</div>
              {item.note && <p className="note-preview">{item.note}</p>}
              <div className="button-row">
                <button className="button button-secondary" onClick={() => onOpenProduct(item.product.id)}>View details</button>
                <button className="button button-quiet danger-text" disabled={busy}
                  onClick={() => void remove(item)}>Remove</button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function ProductSummary({ product }: {
  product: Pick<Product, "name" | "brand" | "category" | "imageUrl">;
}) {
  return (
    <div className="product-summary">
      {product.imageUrl
        ? <img src={product.imageUrl} alt="" className="product-image" />
        : <div className="product-image image-placeholder" aria-hidden="true">{product.category.slice(0, 2)}</div>}
      <div>
        <p className="product-brand">{product.brand} · {humanize(product.category)}</p>
        <h2>{product.name}</h2>
      </div>
    </div>
  );
}

function ProductPage({
  productId,
  onBack,
  onRemoved,
  onError,
}: {
  productId: number;
  onBack: () => void;
  onRemoved: () => void;
  onError: (message: string) => void;
}) {
  const [page, setPage] = useState<ProductPageData | null>(null);
  const [note, setNote] = useState("");
  const [summary, setSummary] = useState("");
  const [priceResult, setPriceResult] = useState<{ bestOffer: Offer | null } | null>(null);
  const [busy, setBusy] = useState(false);
  const [priceBusy, setPriceBusy] = useState(false);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    api.productPage(productId).then((result) => {
      if (!active) return;
      setPage(result);
      setPriceResult(result.savedOffer ? { bestOffer: result.savedOffer } : null);
      setNote(result.note ?? "");
      if (result.discontinued) {
        setNotice("This product has been discontinued and was removed from your saved list.");
      }
    }).catch((error: unknown) => {
      if (active) onError(messageFor(error));
    });
    return () => { active = false; };
  }, [productId, onError]);

  const saveNote = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const result = await api.saveNote(productId, note);
      setNote(result.note ?? "");
      setNotice(result.message);
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const getSummary = async () => {
    setBusy(true);
    setSummary("");
    try {
      const result = await api.summarize(productId);
      setSummary(result.summary);
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const findBestPrice = async () => {
    setPriceBusy(true);
    setPriceResult(null);
    try {
      setPriceResult(await api.bestPrice(productId));
    } catch (error) {
      if (error instanceof ApiError && error.status === 410) {
        setNotice("This product has been discontinued and was removed from your saved list.");
        setPage({ discontinued: true, product: null, note: null, trustLabel: null, savedOffer: null });
      } else {
        onError(messageFor(error));
      }
    } finally {
      setPriceBusy(false);
    }
  };

  if (!page) return <section className="page-section"><p role="status">Loading product…</p></section>;
  if (page.discontinued || !page.product) {
    return <section className="page-section">
      <button className="button button-quiet" onClick={onRemoved}>← Back to saved list</button>
      <div className="empty-panel"><h1>Product unavailable</h1><p>{notice}</p></div>
    </section>;
  }
  const product = page.product;
  return (
    <section className="page-section">
      <button className="button button-quiet" onClick={onBack}>← Saved products</button>
      <article className="card detail-card">
        <div className="detail-header">
          <button type="button" className="button button-secondary saved-offer-button" disabled={busy || priceBusy}
            onClick={() => void findBestPrice()}>{priceBusy ? "Finding best price…" : "Find best price"}</button>
          <ProductSummary product={product} />
        </div>
        <p>{product.description}</p>
        <div className="offer-line saved-offer-line">
          <span>{priceResult && page.savedOffer && priceResult.bestOffer === page.savedOffer
            ? "Offer from recommendation (may have changed)" : "Best price right now:"}</span>
          {priceResult && <strong>{priceResult.bestOffer
            ? `${priceResult.bestOffer.retailer} · ${formatPrice(priceResult.bestOffer.price)} (${humanize(priceResult.bestOffer.condition)})`
            : "No retailer currently meets your settings"}</strong>}
        </div>
        <div className="trust-label">Review trust: {page.trustLabel ?? "Not available"}</div>
        <h2>Specifications</h2>
        <dl className="spec-list">
          {Object.entries(product.attributes).map(([key, value]) => (
            <div key={key}><dt>{formatAttributeLabel(key)}</dt><dd>{formatAttributeValue(value)}</dd></div>
          ))}
        </dl>
      </article>
      <div className="detail-grid">
        <section className="card">
          <div className="section-title-row"><div><p className="eyebrow">REVIEW INSIGHTS</p><h2>Product summary</h2></div>
            <button className="button button-secondary" disabled={busy || priceBusy} onClick={() => void getSummary()}>
              {busy ? "Working…" : "Summarize"}
            </button></div>
          {summary ? <p className="summary-text">{summary}</p> : <p className="muted">Generate a concise summary of product information and reviews.</p>}
        </section>
        <form className="card stack" onSubmit={saveNote}>
          <div><p className="eyebrow">PERSONAL REMINDER</p><h2>Notes</h2></div>
          <label htmlFor="product-note">Your note</label>
          <textarea id="product-note" rows={5} maxLength={4000} value={note}
            onChange={(event) => setNote(event.target.value)} />
          <button className="button button-primary" disabled={busy || priceBusy}>Save note</button>
          {notice && <p role="status" className="muted">{notice}</p>}
        </form>
      </div>
    </section>
  );
}

function ProfileEditor({ onError }: { onError: (message: string) => void }) {
  const [budget, setBudget] = useState("");
  const [excludedBrands, setExcludedBrands] = useState("");
  const [conditions, setConditions] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    api.profile().then((settings) => {
      if (!active) return;
      setBudget(settings.budget === null ? "" : String(settings.budget));
      setExcludedBrands(settings.excludedBrands.join(", "));
      setConditions(settings.conditions);
      setLoaded(true);
    }).catch((error: unknown) => {
      if (active) onError(messageFor(error));
    });
    return () => { active = false; };
  }, [onError]);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setNotice("");
    try {
      const numericBudget = budget.trim() === "" ? null : Number(budget);
      if (numericBudget !== null && (!Number.isFinite(numericBudget) || numericBudget < 0)) {
        throw new Error("Budget must be a non-negative number.");
      }
      const settings = await api.updateProfile({
        budget: numericBudget,
        excludedBrands: excludedBrands.split(",").map((brand) => brand.trim()).filter(Boolean),
        conditions,
      });
      setBudget(settings.budget === null ? "" : String(settings.budget));
      setExcludedBrands(settings.excludedBrands.join(", "));
      setConditions(settings.conditions);
      setNotice("Your preferences have been saved.");
    } catch (error) {
      onError(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page-section narrow-section">
      <div className="page-heading">
        <div><p className="eyebrow">PERSONALIZATION</p><h1>Profile settings</h1>
          <p className="muted">These preferences filter offers and eligible products.</p></div>
      </div>
      <form className="card stack settings-form" onSubmit={save}>
        <label>
          Maximum budget
          <span className="input-prefix"><span>CA$</span><input type="number" min="0" step="0.01"
            value={budget} onChange={(event) => setBudget(event.target.value)} placeholder="No limit" /></span>
        </label>
        <label>
          Excluded brands
          <input value={excludedBrands} onChange={(event) => setExcludedBrands(event.target.value)}
            placeholder="Comma-separated, e.g. Samsung, Apple" />
          <span className="field-hint">Brand names must exist in the product catalog.</span>
        </label>
        <fieldset>
          <legend>Allowed conditions</legend>
          <p className="field-hint">Leave all unchecked to allow every condition.</p>
          <div className="checkbox-list">
            {CONDITIONS.map((condition) => (
              <label className="checkbox-label" key={condition}>
                <input type="checkbox" checked={conditions.includes(condition)}
                  onChange={(event) => setConditions((current) => event.target.checked
                    ? [...current, condition]
                    : current.filter((value) => value !== condition))} />
                {humanize(condition)}
              </label>
            ))}
          </div>
        </fieldset>
        <button className="button button-primary" disabled={busy || !loaded}>
          {busy ? "Saving…" : "Save preferences"}
        </button>
        {notice && <p role="status" className="success-banner">{notice}</p>}
      </form>
    </section>
  );
}

function SharedPage({ token, onHome }: { token: string; onHome: () => void }) {
  const [items, setItems] = useState<SharedProduct[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    api.sharedList(token).then((result) => {
      if (active) setItems(result.items);
    }).catch((problem: unknown) => {
      if (active) setError(messageFor(problem));
    });
    return () => { active = false; };
  }, [token]);

  return (
    <main className="shared-layout">
      <header className="topbar">
        <a className="brand" href="/"><span className="brand-mark" aria-hidden="true">PF</span><span>Product Finder</span></a>
        <span className="public-badge">Shared list · read only</span>
      </header>
      <section className="page-section">
        <p className="eyebrow">A PRODUCT FINDER COLLECTION</p>
        <h1>Shared products</h1>
        <p className="muted">Current best retailer offers are shown below.</p>
        {error && <div className="empty-panel"><h2>This list is unavailable</h2><p>{error}</p><button className="button button-secondary" onClick={onHome}>Go to Product Finder</button></div>}
        {items === null && !error && <p role="status">Loading shared list…</p>}
        {items && items.length === 0 && <div className="empty-panel"><h2>This list is empty</h2></div>}
        {items && items.length > 0 && <div className="saved-grid shared-grid">
          {items.map((item) => <SharedProductCard key={item.model} item={item} />)}
        </div>}
      </section>
    </main>
  );
}

function SharedProductCard({ item }: { item: SharedProduct }) {
  return (
    <article className="card shared-product-card">
      <div className="product-summary">
        {item.imageUrl
          ? <img src={item.imageUrl} alt="" className="product-image" />
          : <div className="product-image image-placeholder" aria-hidden="true">PF</div>}
        <div><p className="product-brand">{item.brand}</p><h2>{item.name}</h2></div>
      </div>
      <div className="offer-line"><span>Best offer</span>
        <strong>{item.price === null || item.retailer === null
          ? "No current eligible offers"
          : `${item.retailer} · ${formatPrice(item.price)}`}</strong>
      </div>
    </article>
  );
}

function ErrorBanner({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  return <div className="error-banner" role="alert"><span>{message}</span>
    <button aria-label="Dismiss error" onClick={onDismiss}>×</button></div>;
}

function messageFor(error: unknown): string {
  if (error instanceof Error) return error.message;
  return "An unexpected error occurred. Please try again.";
}

function formatPrice(value: number): string {
  const amount = new Intl.NumberFormat("en-CA", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
  return `CA$${amount}`;
}

function humanize(value: string): string {
  return value.toLowerCase().split(/[_\s]+/).map((part) => part ? part[0].toUpperCase() + part.slice(1) : "").join(" ");
}

function formatAttributeLabel(key: string): string {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/([A-Z])([A-Z][a-z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .trim()
    .split(/\s+/);
  const lastWord = words.at(-1)?.toLowerCase();
  const units: Record<string, string> = {
    gb: "GB",
    mah: "mAh",
    inches: "inches",
    hz: "Hz",
    hours: "hours",
  };
  const abbreviations: Record<string, string> = { hdr: "HDR", tv: "TV" };
  if (lastWord && units[lastWord]) {
    words.pop();
    return `${humanizeWords(words)} (${units[lastWord]})`;
  }
  return words
    .map((word, index) => {
      const normalized = word.toLowerCase();
      return abbreviations[normalized]
        ?? (index === 0 ? normalized.charAt(0).toUpperCase() + normalized.slice(1) : normalized);
    })
    .join(" ");
}

function formatAttributeValue(value: unknown): string {
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (Array.isArray(value)) return value.map(formatSimpleValue).join(", ");
  if (value !== null && typeof value === "object") {
    return Object.entries(value)
      .map(([name, nestedValue]) => `${formatAttributeLabel(name)}: ${formatAttributeValue(nestedValue)}`)
      .join(", ");
  }
  return formatSimpleValue(value);
}

function formatSimpleValue(value: unknown): string {
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "string" && /[_-]/.test(value)) {
    return humanizeWords(value.split(/[_-]+/));
  }
  return String(value);
}

function humanizeWords(words: string[]): string {
  return words
    .map((word, index) => {
      const normalized = word.toLowerCase();
      return index === 0
        ? normalized.charAt(0).toUpperCase() + normalized.slice(1)
        : normalized;
    })
    .join(" ");
}
