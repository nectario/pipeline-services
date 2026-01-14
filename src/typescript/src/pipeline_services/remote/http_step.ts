export class RemoteSpec {
  public endpoint: string;
  public timeout_millis: number;
  public retries: number;
  public method: string;
  public headers: Record<string, string> | null;

  constructor(endpoint: string) {
    this.endpoint = endpoint;
    this.timeout_millis = 1000;
    this.retries = 0;
    this.method = "POST";
    this.headers = null;
  }
}

export class RemoteDefaults {
  public base_url: string;
  public timeout_millis: number;
  public retries: number;
  public method: string;
  public headers: Record<string, string> | null;

  constructor() {
    this.base_url = "";
    this.timeout_millis = 1000;
    this.retries = 0;
    this.method = "POST";
    this.headers = null;
  }

  resolve_endpoint(endpoint_or_path: string): string {
    if (endpoint_or_path.startsWith("http://") || endpoint_or_path.startsWith("https://")) {
      return endpoint_or_path;
    }
    if (this.base_url === "") {
      return endpoint_or_path;
    }
    if (this.base_url.endsWith("/") && endpoint_or_path.startsWith("/")) {
      return this.base_url + endpoint_or_path.slice(1);
    }
    if (!this.base_url.endsWith("/") && !endpoint_or_path.startsWith("/")) {
      return this.base_url + "/" + endpoint_or_path;
    }
    return this.base_url + endpoint_or_path;
  }

  to_spec(endpoint_or_path: string): RemoteSpec {
    const resolved_endpoint = this.resolve_endpoint(endpoint_or_path);
    const spec = new RemoteSpec(resolved_endpoint);
    spec.timeout_millis = this.timeout_millis;
    spec.retries = this.retries;
    spec.method = this.method;
    spec.headers = this.headers;
    return spec;
  }
}

export async function sleep_ms(delay_millis: number): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, delay_millis));
}

export async function http_step(spec: RemoteSpec, input_value: unknown): Promise<string> {
  let headers_value: Record<string, string> = {};
  if (spec.headers != null) {
    headers_value = spec.headers;
  }

  const json_body = JSON.stringify(input_value);
  let last_error_message = "";

  let attempt_index = 0;
  while (attempt_index < spec.retries + 1) {
    try {
      const timeout_millis = Number(spec.timeout_millis);
      const method_value = String(spec.method);

      const abort_controller = new AbortController();
      const abort_timeout = setTimeout(() => abort_controller.abort(), timeout_millis);
      try {
        if (spec.method === "GET") {
          const response = await fetch(spec.endpoint, {
            method: method_value,
            headers: headers_value,
            signal: abort_controller.signal,
          });
          const response_body = await response.text();
          if (!response.ok) {
            throw new Error("HTTP " + String(response.status) + " body=" + response_body);
          }
          return String(response_body);
        }

        const content_headers: Record<string, string> = { "Content-Type": "application/json" };
        Object.assign(content_headers, headers_value);
        const response = await fetch(spec.endpoint, {
          method: method_value,
          headers: content_headers,
          body: json_body,
          signal: abort_controller.signal,
        });
        const response_body = await response.text();
        if (!response.ok) {
          throw new Error("HTTP " + String(response.status) + " body=" + response_body);
        }
        return String(response_body);
      } finally {
        clearTimeout(abort_timeout);
      }
    } catch (caught_error) {
      last_error_message = String(caught_error);
      if (attempt_index < spec.retries) {
        const backoff_seconds = 0.05 * (attempt_index + 1);
        await sleep_ms(backoff_seconds * 1000.0);
      }
      attempt_index += 1;
    }
  }

  throw new Error(last_error_message);
}

