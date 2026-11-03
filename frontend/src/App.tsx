import { useEffect, useState } from "react";

type HealthStatus = "checking" | "up" | "unavailable";

export default function App() {
  const [health, setHealth] = useState<HealthStatus>("checking");

  useEffect(() => {
    const controller = new AbortController();

    fetch("/actuator/health", { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Backend returned ${response.status}`);
        }
        return response.json();
      })
      .then((result: { status?: string }) => {
        setHealth(result.status === "UP" ? "up" : "unavailable");
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }
        setHealth("unavailable");
      });

    return () => controller.abort();
  }, []);

  return (
    <main className="shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Product Finder home">
          <span className="brand-mark" aria-hidden="true">
            PF
          </span>
          <span>Product Finder</span>
        </a>
        <span className="environment">Local development</span>
      </header>

      <section className="welcome" aria-labelledby="welcome-title">
        <p className="eyebrow">PRODUCT DISCOVERY</p>
        <h1 id="welcome-title">Find the right product for you.</h1>
        <p className="intro">
          Compare products and retailer prices, guided by what matters to you.
        </p>
        <div className="status" role="status">
          <span className={`status-dot ${health}`} />
          {health === "checking" && "Checking backend connection…"}
          {health === "up" && "Backend is running"}
          {health === "unavailable" &&
            "Backend is unavailable. Start the Java service to connect."}
        </div>
      </section>

      <section className="feature-grid" aria-label="Planned application areas">
        <article className="feature-card">
          <span className="card-index">01</span>
          <h2>AI product search</h2>
          <p>Describe what you need and compare a shortlist that fits.</p>
        </article>
        <article className="feature-card">
          <span className="card-index">02</span>
          <h2>Price comparison</h2>
          <p>See current offers across retailers for the same product.</p>
        </article>
        <article className="feature-card">
          <span className="card-index">03</span>
          <h2>Your saved list</h2>
          <p>Keep track of products, notes, and review insights.</p>
        </article>
      </section>
    </main>
  );
}
