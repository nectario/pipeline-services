import { PipelineJsonLoader, PipelineRegistry } from "../../index.js";

async function main(): Promise<void> {
  const jsonText = `
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "actions": [
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

  const pipeline = new PipelineJsonLoader().load_str(
    jsonText,
    new PipelineRegistry(),
  );
  const output = await pipeline.run("ignored");
  // eslint-disable-next-line no-console
  console.log(String(output));
}

void main();
