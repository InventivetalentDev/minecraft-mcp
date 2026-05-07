import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

// Paper publishes a JSON search index that we can query without scraping HTML.
const JAVADOC_BASES: Record<string, string> = {
  paper: "https://jd.papermc.io/paper/1.21.4",
  folia: "https://jd.papermc.io/folia/1.21.4",
  velocity: "https://jd.papermc.io/velocity/3.4.0",
};

interface JavadocEntry {
  l: string;  // label (class or member name)
  u?: string; // URL fragment
  d?: string; // description
  p?: string; // package
}

async function fetchSearchIndex(base: string): Promise<JavadocEntry[]> {
  const url = `${base}/search-index.zip`;
  // Paper actually ships a JS file; fall back to the type-search-index
  const res = await fetch(`${base}/type-search-index.js`);
  if (!res.ok) throw new Error(`HTTP ${res.status} fetching javadoc index`);
  const text = await res.text();
  // The file is:  typeSearchIndex = [{"l":"All Classes","u":"allclasses-index.html"},...];
  const match = text.match(/=\s*(\[[\s\S]*?\]);/);
  if (!match) throw new Error("Could not parse type-search-index.js");
  return JSON.parse(match[1]) as JavadocEntry[];
}

async function fetchMemberIndex(base: string): Promise<JavadocEntry[]> {
  const res = await fetch(`${base}/member-search-index.js`);
  if (!res.ok) return [];
  const text = await res.text();
  const match = text.match(/=\s*(\[[\s\S]*?\]);/);
  if (!match) return [];
  return JSON.parse(match[1]) as JavadocEntry[];
}

export function registerJavadocTools(server: McpServer) {
  server.tool(
    "search_javadocs",
    "Search the Paper/Folia/Velocity published Javadoc index for classes, methods, or fields. " +
      "Returns matching entries with their URL so you can fetch full docs if needed.",
    {
      query: z
        .string()
        .describe(
          "Search term — simple substring match against class/member names"
        ),
      project: z
        .enum(["paper", "folia", "velocity"])
        .default("paper")
        .describe("Which project's javadocs to search"),
      type: z
        .enum(["class", "member", "all"])
        .default("all")
        .describe("Restrict search to classes, members (methods/fields), or both"),
      limit: z.number().int().min(1).max(50).default(20),
    },
    async ({ query, project, type, limit }) => {
      const base = JAVADOC_BASES[project];
      const q = query.toLowerCase();

      const results: Array<{ kind: string; label: string; url: string; description?: string }> = [];

      if (type !== "member") {
        try {
          const classes = await fetchSearchIndex(base);
          for (const entry of classes) {
            if (!entry.l || entry.l === "All Classes") continue;
            if (entry.l.toLowerCase().includes(q) || entry.p?.toLowerCase().includes(q)) {
              const path = entry.u ?? `${entry.p?.replace(/\./g, "/")}/${entry.l}.html`;
              results.push({
                kind: "class",
                label: `${entry.p}.${entry.l}`,
                url: `${base}/${path}`,
                description: entry.d,
              });
            }
            if (results.length >= limit * 2) break;
          }
        } catch (e) {
          results.push({ kind: "error", label: String(e), url: "" });
        }
      }

      if (type !== "class") {
        try {
          const members = await fetchMemberIndex(base);
          for (const entry of members) {
            if (entry.l?.toLowerCase().includes(q)) {
              results.push({
                kind: "member",
                label: entry.l,
                url: entry.u ? `${base}/${entry.u}` : base,
                description: entry.d,
              });
            }
            if (results.length >= limit * 2) break;
          }
        } catch {
          // member index may not exist; silently skip
        }
      }

      const trimmed = results.slice(0, limit);
      return {
        content: [{ type: "text", text: JSON.stringify(trimmed, null, 2) }],
      };
    }
  );

  server.tool(
    "fetch_javadoc_page",
    "Fetch the raw HTML of a Javadoc page and extract the plain-text description, method signatures, and field summaries. Useful for reading full API documentation for a specific class.",
    {
      url: z
        .string()
        .url()
        .describe("Full URL of the Javadoc page (as returned by search_javadocs)"),
    },
    async ({ url }) => {
      // Only allow known javadoc domains
      const allowed = Object.values(JAVADOC_BASES);
      if (!allowed.some((base) => url.startsWith(base))) {
        return {
          content: [{ type: "text", text: "URL not in allowed javadoc domains." }],
          isError: true,
        };
      }

      const res = await fetch(url);
      if (!res.ok) {
        return {
          content: [{ type: "text", text: `HTTP ${res.status}` }],
          isError: true,
        };
      }
      const html = await res.text();

      // Strip HTML tags to give a readable plaintext summary (lightweight extraction)
      const text = html
        .replace(/<script[\s\S]*?<\/script>/gi, "")
        .replace(/<style[\s\S]*?<\/style>/gi, "")
        .replace(/<[^>]+>/g, " ")
        .replace(/&nbsp;/g, " ")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&")
        .replace(/[ \t]{2,}/g, " ")
        .replace(/\n{3,}/g, "\n\n")
        .trim();

      // Truncate to avoid overwhelming context
      const truncated =
        text.length > 20_000 ? text.slice(0, 20_000) + "\n\n[truncated]" : text;

      return { content: [{ type: "text", text: truncated }] };
    }
  );
}
