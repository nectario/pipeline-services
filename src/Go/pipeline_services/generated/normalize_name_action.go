package generated

import (
  "strings"
)

func NormalizeNameAction(textValue string) string {
  outputValue := textValue
  outputValue = collapseWhitespace(outputValue)
  outputValue = strings.TrimSpace(outputValue)
  outputValue = titleCaseTokens(outputValue)
  return outputValue
}

func collapseWhitespace(textValue string) string {
  outputValue := strings.Builder{}
  outputValue.Grow(len(textValue))
  previousWasSpace := false
  for characterIndex := 0; characterIndex < len(textValue); characterIndex++ {
    characterValue := textValue[characterIndex]
    if characterValue == ' ' || characterValue == '\t' || characterValue == '\n' || characterValue == '\r' {
      if !previousWasSpace {
        outputValue.WriteRune(' ')
        previousWasSpace = true
      }
      continue
    }
    previousWasSpace = false
    outputValue.WriteByte(characterValue)
  }
  return outputValue.String()
}

func titleCaseTokens(textValue string) string {
  tokens := strings.Split(textValue, " ")
  normalizedTokens := make([]string, 0, len(tokens))
  for tokenIndex := 0; tokenIndex < len(tokens); tokenIndex++ {
    token := tokens[tokenIndex]
    if token == "" {
      continue
    }
    lowerToken := strings.ToLower(token)
    firstCharacter := lowerToken[:1]
    remainder := ""
    if len(lowerToken) > 1 {
      remainder = lowerToken[1:]
    }
    normalizedTokens = append(normalizedTokens, strings.ToUpper(firstCharacter)+remainder)
  }
  return strings.Join(normalizedTokens, " ")
}
