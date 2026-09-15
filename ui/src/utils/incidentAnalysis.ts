import type { Evidence } from "../types";

/** A real exception pulled out of a raw Loki log line's embedded `stack_trace` field.
 *  The backend never structures this — evidence.value is just the raw log JSON as
 *  text — so this is parsed client-side, once, for display. */
export interface ExceptionDetails {
  exceptionType: string;
  message: string;
  /** e.g. "OrderService.applyLoyaltyDiscount" — first stack frame belonging to this
   *  app's own code (com.airca.*), not a framework/JDK frame. */
  method: string | null;
  /** e.g. "OrderService.java:89" */
  location: string | null;
  rawStackTrace: string;
}

/** A lighter-weight error signal from this platform's own structured "order_failed"
 *  style log lines (errorType=... httpStatus=... reason="..."), for cases with no full
 *  Java stack trace (e.g. a controlled PAYMENT_TIMEOUT, not an unhandled exception). */
export interface ErrorSignal {
  errorType: string;
  httpStatus: string | null;
  reason: string | null;
  occurrences: number;
}

const APP_FRAME = /^com\.airca\./;

function unescapeJsonString(raw: string): string {
  try {
    return JSON.parse(`"${raw}"`);
  } catch {
    return raw;
  }
}

export function extractExceptions(evidence: Evidence[]): ExceptionDetails[] {
  const results: ExceptionDetails[] = [];
  const seen = new Set<string>();

  for (const e of evidence) {
    if (!e.value) continue;
    const stackTraceMatches = e.value.matchAll(/"stack_trace":"((?:[^"\\]|\\.)*)"/g);
    for (const m of stackTraceMatches) {
      const trace = unescapeJsonString(m[1]);
      const excMatch = trace.match(/^([\w.$]+(?:Exception|Error))\s*:\s*(.*)$/m);
      if (!excMatch) continue;

      const exceptionType = excMatch[1].split(".").pop() ?? excMatch[1];
      const message = excMatch[2].trim();

      const frames = [...trace.matchAll(/at ([\w.$]+)\(([\w.]+\.java):(\d+)\)/g)];
      const appFrame = frames.find((f) => APP_FRAME.test(f[1]));
      const method = appFrame ? appFrame[1].split(".").slice(-2).join(".") : null;
      const location = appFrame ? `${appFrame[2]}:${appFrame[3]}` : null;

      const key = `${exceptionType}|${message}|${location}`;
      if (!seen.has(key)) {
        seen.add(key);
        results.push({ exceptionType, message, method, location, rawStackTrace: trace });
      }
    }
  }
  return results;
}

export function extractErrorSignals(evidence: Evidence[]): ErrorSignal[] {
  const counts = new Map<string, ErrorSignal>();

  for (const e of evidence) {
    if (!e.value) continue;
    // Loki evidence text embeds a log line's JSON `message` field as already-escaped
    // text, so a literal `reason="..."` in the app's log line shows up here as
    // `reason=\"...\"` — exactly one backslash before each quote, not a bare quote.
    const matches = e.value.matchAll(
      /errorType=(\w+)(?:\s+httpStatus=(\d+))?(?:\s+reason=\\"((?:[^"\\]|\\.)*)\\")?/g,
    );
    for (const m of matches) {
      const [, errorType, httpStatus, reason] = m;
      const key = `${errorType}|${httpStatus ?? ""}|${reason ?? ""}`;
      const existing = counts.get(key);
      if (existing) {
        existing.occurrences += 1;
      } else {
        counts.set(key, {
          errorType,
          httpStatus: httpStatus ?? null,
          reason: reason ? unescapeJsonString(reason) : null,
          occurrences: 1,
        });
      }
    }
  }
  return [...counts.values()].sort((a, b) => b.occurrences - a.occurrences);
}

const SOURCE_LABELS: Record<string, string> = {
  Prometheus: "Metrics",
  Loki: "Logs",
  Jaeger: "Traces",
  RAG: "Knowledge Base",
};

export function sourceLabel(source: string): string {
  return SOURCE_LABELS[source] ?? source;
}
