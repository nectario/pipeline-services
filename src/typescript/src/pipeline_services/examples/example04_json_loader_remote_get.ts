import { PipelineJsonLoader, PipelineRegistry } from "../../index.js";

async function main(): Promise<void> {
  const json_text = `
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "http://127.0.0.1:8765/remote_hello.txt",
        "method": "GET",
        "timeoutMillis": 1000,
        "retries": 0
      }
    }
  ]
}
`;

  const registry = new PipelineRegistry();
  const loader = new PipelineJsonLoader();
  const pipeline = loader.load_str(json_text, registry);
  const result = await pipeline.run("ignored");
  // eslint-disable-next-line no-console
  console.log(result.context);
}

void main();
