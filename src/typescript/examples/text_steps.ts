import { shortCircuit } from "../core/short_circuit.js";

export class TextSteps {
  static strip(textValue: string | null | undefined): string {
    if (textValue == null) {
      return "";
    }
    const textString = String(textValue);
    return textString.trim();
  }

  static normalizeWhitespace(textValue: string): string {
    const textString = String(textValue);
    const result = textString.replace(/\s+/g, " ");
    return result;
  }

  static disallowEmoji(textValue: string): string {
    const textString = String(textValue);
    const hasEmoji = /[\u2600-\u26FF\u2700-\u27BF]/.test(textString);
    if (hasEmoji) {
      throw new Error("Emoji not allowed");
    }
    return textString;
  }

  static truncateAt280(textValue: string): string {
    const textString = String(textValue);
    if (textString.length > 280) {
      shortCircuit(textString.slice(0, 280));
    }
    return textString;
  }
}
