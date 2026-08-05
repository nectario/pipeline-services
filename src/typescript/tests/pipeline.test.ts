import assert from "node:assert/strict";
import { createServer, Server } from "node:http";
import { AddressInfo } from "node:net";
import test from "node:test";

import {
  ActionControl,
  InvalidErrorHandlerError,
  Pipeline,
  PipelineJsonLoader,
  PipelineObserver,
  PipelineProvider,
  PipelineProviderMode,
  PipelineRegistry,
  PipelineRouter,
  RemoteSpec,
  http_step,
  shortCircuit,
} from "../src/index.js";

function appendAndStop(value: string): string {
  shortCircuit();
  return value + "S";
}

function fail(value: string): string {
  void value;
  throw new Error("boom");
}

test("run returns context and runDetailed uses the same semantics", async () => {
  const pipeline = new Pipeline<string>("simple")
    .addPreAction((value) => value + "P")
    .addAction((value) => value + "A")
    .addPostAction((value) => value + "Z");

  assert.equal(await pipeline.run("X"), "XPAZ");
  const result = await pipeline.runDetailed("X");
  assert.equal(result.context, "XPAZ");
  assert.equal(result.actionTimings.length, 3);
});

test("shortCircuit preserves phase rules", async () => {
  const main = new Pipeline<string>("main")
    .addAction(appendAndStop)
    .addAction((value) => value + "B")
    .addPostAction((value) => value + "P");
  assert.equal(await main.run("X"), "XSP");

  const pre = new Pipeline<string>("pre")
    .addPreAction(appendAndStop)
    .addPreAction((value) => value + "2")
    .addAction((value) => value + "M")
    .addPostAction((value) => value + "P");
  assert.equal(await pre.run("X"), "XS2P");

  const post = new Pipeline<string>("post")
    .addAction((value) => value + "M")
    .addPostAction(appendAndStop)
    .addPostAction((value) => value + "2");
  const result = await post.runDetailed("X");
  assert.equal(result.context, "XMS2");
  assert.equal(result.shortCircuited, true);
});

test("exception policies capture errors and always run postActions", async () => {
  const continuing = new Pipeline<string>("continue", false)
    .addAction((value) => value + "A")
    .addAction(fail)
    .addAction((value) => value + "B");
  const continued = await continuing.runDetailed("X");
  assert.equal(continued.context, "XAB");
  assert.equal(continued.shortCircuited, false);
  assert.equal(continued.errors[0].actionIndex, 1);

  const stopping = new Pipeline<string>("stop", true)
    .addAction((value) => value + "A")
    .addAction(fail)
    .addAction((value) => value + "B")
    .addPostAction((value) => value + "P");
  const stopped = await stopping.runDetailed("X");
  assert.equal(stopped.context, "XAP");
  assert.equal(stopped.shortCircuited, true);
});

test("invalid error handlers fail after cleanup", async () => {
  const calls: Array<string> = [];
  const pipeline = new Pipeline<string>("bad")
    .onError(() => null as unknown as string)
    .addAction(fail)
    .addPostAction((value) => {
      calls.push("post1");
      return value;
    })
    .addPostAction((value) => {
      calls.push("post2");
      return value;
    });

  await assert.rejects(
    pipeline.run("X"),
    InvalidErrorHandlerError,
  );
  assert.deepEqual(calls, ["post1", "post2"]);
});

test("nested and concurrent async runs keep ambient state isolated", async () => {
  const inner = new Pipeline<string>("inner")
    .addAction(appendAndStop)
    .addAction((value) => value + "I2")
    .addPostAction((value) => value + "IP");
  const outer = new Pipeline<string>("outer")
    .addAction(async (value) => value + ":" + (await inner.run("inner")))
    .addAction((value) => value + "O2");
  assert.equal(await outer.run("outer"), "outer:innerSIPO2");

  let started = 0;
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });

  const shared = new Pipeline<string>("shared")
    .addAction(async (value) => {
      started += 1;
      if (started === 2) {
        release();
      }
      await gate;
      if (value === "stop") {
        shortCircuit();
      }
      return value + "A";
    })
    .addAction((value) => value + "B")
    .addPostAction((value) => value + "P");
  const provider = PipelineProvider.singleton(shared);

  const [stopped, continued] = await Promise.all([
    provider.run("stop"),
    provider.run("go"),
  ]);
  assert.equal(stopped, "stopAP");
  assert.equal(continued, "goABP");
});

test("plan freezing and compatibility control use the same runner", async () => {
  const pipeline = new Pipeline<string>("frozen")
    .addAction((value) => value + "A");
  assert.equal(await pipeline.run("X"), "XA");
  assert.equal(pipeline.isFrozen(), true);
  assert.throws(
    () => pipeline.addAction((value) => value + "B"),
    /frozen/,
  );
  assert.throws(shortCircuit, /active Pipeline run/);

  const legacy = new Pipeline<string>("legacy")
    .addAction((value: string, control: ActionControl<string>) => {
      control.short_circuit();
      return value + "L";
    })
    .addAction((value) => value + "B");
  assert.equal(await legacy.run("X"), "XL");
});

test("observers cannot control execution", async () => {
  const events: Array<string> = [];
  const observer: PipelineObserver = {
    onActionStarted: (_pipeline, phase, index, name) => {
      events.push(`${phase}:${index}:${name}`);
      shortCircuit();
    },
  };
  const pipeline = new Pipeline<string>("observed")
    .observer(observer)
    .addAction((value) => value + "A")
    .addAction((value) => value + "B");
  assert.equal(await pipeline.run("X"), "XAB");
  assert.deepEqual(events, ["actions:0:s0", "actions:1:s1"]);
});

test("provider modes are eager, fixed, and round robin", async () => {
  let created = 0;
  const factory = (): Pipeline<string> => {
    created += 1;
    const instanceId = created;
    return new Pipeline<string>(`p${instanceId}`)
      .addAction((value) => value + String(instanceId));
  };

  const pooled = PipelineProvider.pooled(factory, 3);
  assert.equal(created, 3);
  assert.equal(pooled.mode, PipelineProviderMode.POOLED);
  assert.equal(pooled.instanceCount, 3);
  assert.deepEqual(
    await Promise.all([
      pooled.run(""),
      pooled.run(""),
      pooled.run(""),
      pooled.run(""),
      pooled.run(""),
    ]),
    ["1", "2", "3", "1", "2"],
  );

  const singleton = PipelineProvider.singleton(
    new Pipeline<string>("singleton"),
  );
  assert.equal(singleton.getPipeline(), singleton.getPipeline());

  const perEvent = PipelineProvider.newInstancePerEvent(factory);
  assert.notEqual(perEvent.getPipeline(), perEvent.getPipeline());
});

test("PipelineRouter selects providers without changing lifecycle", () => {
  const trade = PipelineProvider.singleton(
    new Pipeline<string>("trade"),
  );
  const quote = PipelineProvider.singleton(
    new Pipeline<string>("quote"),
  );
  const router = new PipelineRouter<string, string>(
    (event) => (event === "TRADE" ? trade : quote),
  );
  assert.equal(router.getPipelineProvider("TRADE"), trade);
  assert.equal(router.getPipelineProvider("QUOTE"), quote);
});

async function identityAction(value: unknown): Promise<unknown> {
  return value;
}

test("JSON loader accepts canonical vocabulary", async () => {
  const registry = new PipelineRegistry();
  registry.register_unary("identity", identityAction);

  const pipeline = new PipelineJsonLoader().load_str(
    `{
      "pipeline": "json",
      "preActions": [{"$local": "identity"}],
      "actions": [{"$local": "identity"}],
      "postActions": [{"$local": "identity"}]
    }`,
    registry,
  );
  assert.equal(await pipeline.run("ok"), "ok");
});

const remoteFixtureBody = "Hello from remote fixture\n";

function fixtureRequestHandler(request: any, response: any): void {
  if (String(request.url ?? "") === "/remote_hello.txt") {
    response.statusCode = 200;
    response.end(remoteFixtureBody);
    return;
  }
  response.statusCode = 404;
  response.end("not found");
}

class FixtureServer {
  private server: Server | null = null;

  async start(): Promise<number> {
    this.server = createServer(fixtureRequestHandler);
    await new Promise<void>((resolve) =>
      this.server!.listen(0, "127.0.0.1", resolve),
    );
    return (this.server.address() as AddressInfo).port;
  }

  async stop(): Promise<void> {
    if (this.server == null) {
      return;
    }
    const server = this.server;
    this.server = null;
    await new Promise<void>((resolve) =>
      server.close(() => resolve()),
    );
  }
}

test("remote adapters execute as ordinary Actions", async () => {
  const server = new FixtureServer();
  const port = await server.start();
  try {
    const spec = new RemoteSpec(
      `http://127.0.0.1:${port}/remote_hello.txt`,
    );
    spec.method = "GET";
    assert.equal(await http_step(spec, "ignored"), remoteFixtureBody);

    const pipeline = new PipelineJsonLoader().load_str(
      `{
        "pipeline": "remote",
        "actions": [{
          "$remote": {
            "endpoint": "http://127.0.0.1:${port}/remote_hello.txt",
            "method": "GET"
          }
        }]
      }`,
      new PipelineRegistry(),
    );
    assert.equal(await pipeline.run("ignored"), remoteFixtureBody);
  } finally {
    await server.stop();
  }
});
