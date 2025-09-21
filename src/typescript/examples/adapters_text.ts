import { TextSteps } from "./text_steps.js";

export class TextStripStep {
  apply(value: string): string {
    return TextSteps.strip(value);
  }
}

export class TextNormalizeStep {
  apply(value: string): string {
    return TextSteps.normalizeWhitespace(value);
  }
}
