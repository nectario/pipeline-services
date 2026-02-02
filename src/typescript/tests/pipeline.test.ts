import assert from "node:assert/strict";
import { createServer, Server } from "node:http";
import { AddressInfo } from "node:net";
import test from "node:test";

import { ActionControl, Pipeline, PipelineJsonLoader, PipelineRegistry, RemoteSpec, http_step } from "../src/index.js";

async function identity_action(value: unknown): Promise<unknown> {
  return value;
}

class CallRecorderActions {
  public calls: Array<string>;

  constructor() {
    this.calls = [];
  }

  public pre_action = async (ctx: unknown): Promise<string> => {
    this.calls.push("pre");
    return String(ctx) + "pre|";
  };

  public action_one = async (ctx: unknown): Promise<string> => {
    this.calls.push("a1");
    return String(ctx) + "a1|";
  };

  public action_two_short_circuit = async (ctx: unknown, control: ActionControl): Promise<string> => {
    this.calls.push("a2");
    control.short_circuit();
    return String(ctx) + "a2|";
  };

  public action_three = async (ctx: unknown): Promise<string> => {
    this.calls.push("a3");
    return String(ctx) + "a3|";
  };

  public post_action = async (ctx: unknown): Promise<string> => {
    this.calls.push("post");
    return String(ctx) + "post|";
  };
}

class ExceptionActions {
  public calls: Array<string>;

  constructor() {
    this.calls = [];
  }

  public failing_action = async (ctx: unknown): Promise<unknown> => {
    this.calls.push("fail");
    void ctx;
    throw new Error("boom");
  };

  public later_action = async (ctx: unknown): Promise<string> => {
    this.calls.push("later");
    return String(ctx) + "|later";
  };

  public post_action = async (ctx: unknown): Promise<string> => {
    this.calls.push("post");
    return String(ctx) + "|post";
  };
}

const remote_fixture_body = "Hello from remote fixture\n";

function fixture_request_handler(request: any, response: any): void {
  const request_url = String(request.url ?? "");
  if (request_url === "/remote_hello.txt") {
    response.statusCode = 200;
    response.setHeader("Content-Type", "text/plain; charset=utf-8");
    response.end(remote_fixture_body);
    return;
  }
  response.statusCode = 404;
  response.end("not found");
}

class FixtureServer {
  private server: Server | null;

  constructor() {
    this.server = null;
  }

  async start(): Promise<number> {
    if (this.server != null) {
      throw new Error("Server already started");
    }
    this.server = createServer(fixture_request_handler);
    await new Promise<void>((resolve) => this.server!.listen(0, "127.0.0.1", resolve));
    const address = this.server.address() as AddressInfo;
    return address.port;
  }

  async stop(): Promise<void> {
    if (this.server == null) {
      return;
    }
    const server_to_close = this.server;
    this.server = null;
    await new Promise<void>((resolve) => server_to_close.close(() => resolve()));
  }
}

async function test_short_circuit_stops_main_only(): Promise<void> {
  const actions = new CallRecorderActions();

  const pipeline = new Pipeline("t", true);
  pipeline.add_pre_action(actions.pre_action);
  pipeline.add_action(actions.action_one);
  pipeline.add_action(actions.action_two_short_circuit);
  pipeline.add_action(actions.action_three);
  pipeline.add_post_action(actions.post_action);

  const result = await pipeline.run("");
  assert.equal(result.short_circuited, true);
  assert.deepEqual(actions.calls, ["pre", "a1", "a2", "post"]);
}

async function test_short_circuit_on_exception_stops_main(): Promise<void> {
  const actions = new ExceptionActions();

  const pipeline = new Pipeline("t", true);
  pipeline.add_action(actions.failing_action);
  pipeline.add_action(actions.later_action);
  pipeline.add_post_action(actions.post_action);

  const result = await pipeline.run("start");
  assert.equal(result.short_circuited, true);
  assert.equal(result.errors.length, 1);
  assert.deepEqual(actions.calls, ["fail", "post"]);
}

async function test_continue_on_exception_runs_remaining_actions(): Promise<void> {
  const actions = new ExceptionActions();

  const pipeline = new Pipeline("t", false);
  pipeline.add_action(actions.failing_action);
  pipeline.add_action(actions.later_action);

  const result = await pipeline.run("start");
  assert.equal(result.short_circuited, false);
  assert.equal(result.errors.length, 1);
  assert.equal(result.context, "start|later");
  assert.deepEqual(actions.calls, ["fail", "later"]);
}

async function test_json_loader_actions_alias(): Promise<void> {
  const registry = new PipelineRegistry();
  registry.register_unary("identity", identity_action);

  const json_text = `
{
  "pipeline": "t",
  "type": "unary",
  "actions": [
    {"$local": "identity"}
  ]
}
`;
  const loader = new PipelineJsonLoader();
  const pipeline = loader.load_str(json_text, registry);
  const result = await pipeline.run("ok");
  assert.equal(result.context, "ok");
}

async function test_http_step_get(): Promise<void> {
  const server = new FixtureServer();
  const port = await server.start();
  try {
    const spec = new RemoteSpec("http://127.0.0.1:" + String(port) + "/remote_hello.txt");
    spec.method = "GET";
    const response_body = await http_step(spec, "ignored");
    assert.equal(response_body, remote_fixture_body);
  } finally {
    await server.stop();
  }
}

async function test_json_loader_remote_get(): Promise<void> {
  const server = new FixtureServer();
  const port = await server.start();
  try {
    const json_text = `
{
  "pipeline": "t",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "http://127.0.0.1:${port}/remote_hello.txt",
        "method": "GET"
      }
    }
  ]
}
`;
    const registry = new PipelineRegistry();
    const loader = new PipelineJsonLoader();
    const pipeline = loader.load_str(json_text, registry);
    const result = await pipeline.run("ignored");
    assert.equal(result.context, remote_fixture_body);
  } finally {
    await server.stop();
  }
}

test("short-circuit stops main only", test_short_circuit_stops_main_only);
test("shortCircuitOnException stops main", test_short_circuit_on_exception_stops_main);
test("continue-on-exception runs remaining actions", test_continue_on_exception_runs_remaining_actions);
test("JSON loader supports actions alias", test_json_loader_actions_alias);
test("remote http_step GET", test_http_step_get);
test("JSON loader $remote GET", test_json_loader_remote_get);
