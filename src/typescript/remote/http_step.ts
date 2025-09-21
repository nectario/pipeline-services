export interface RemoteSpecInit {
  endpoint: string;
  timeoutMillis?: number;
  retries?: number;
  headers?: Record<string, string>;
  method?: "GET" | "POST";
  toJson?: (inputValue: unknown) => string;
  fromJson?: (text: string) => unknown;
}

export class RemoteSpec {
  endpoint: string;
  timeoutMillis: number;
  retries: number;
  headers: Record<string, string>;
  method: "GET" | "POST";
  toJson: (inputValue: unknown) => string;
  fromJson: (text: string) => unknown;

  constructor(init: RemoteSpecInit) {
    this.endpoint = init.endpoint;
    this.timeoutMillis = init.timeoutMillis ?? 1000;
    this.retries = init.retries ?? 0;
    this.headers = { ...(init.headers ?? {}) };
    this.method = (init.method ?? "POST").toUpperCase() as "GET" | "POST";
    this.toJson = init.toJson ?? ((inputValue: unknown) => JSON.stringify(inputValue));
    this.fromJson = init.fromJson ?? ((text: string) => JSON.parse(text));
  }
}

export function httpStep(spec: RemoteSpec) {
  return async function callRemote(inputValue: unknown): Promise<unknown> {
    const bodyText = spec.toJson(inputValue);
    let lastError: unknown | undefined;

    for (let attemptIndex = 0; attemptIndex < spec.retries + 1; attemptIndex += 1) {
      try {
        if (spec.method === "GET") {
          let queryString = "";
          try {
            const objectValue = JSON.parse(bodyText);
            if (objectValue && typeof objectValue === "object") {
              const params = new URLSearchParams();
              for (const [key, value] of Object.entries(objectValue as Record<string, unknown>)) {
                if (Array.isArray(value)) {
                  for (const item of value) {
                    params.append(key, String(item));
                  }
                } else if (value != null) {
                  params.append(key, String(value));
                }
              }
              queryString = params.toString();
            } else {
              queryString = String(objectValue);
            }
          } catch {
            queryString = bodyText ? String(bodyText) : "";
          }
          let url = spec.endpoint;
          if (queryString.length > 0) {
            url = url + (url.includes("?") ? "&" : "?") + queryString;
          }
          const response = await fetch(url, {
            method: "GET",
            headers: spec.headers
          });
          const text = await response.text();
          return spec.fromJson(text);
        } else {
          const response = await fetch(spec.endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json", ...spec.headers },
            body: bodyText
          });
          const text = await response.text();
          return spec.fromJson(text);
        }
      } catch (caughtError) {
        lastError = caughtError;
        if (attemptIndex < spec.retries) {
          const delayMillis = 50 * (attemptIndex + 1);
          await new Promise<void>(resolve => setTimeout(resolve, delayMillis));
          continue;
        } else {
          throw caughtError;
        }
      }
    }

    if (lastError != null) {
      throw lastError;
    }
    return inputValue;
  };
}
