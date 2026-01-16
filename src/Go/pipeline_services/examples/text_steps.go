package examples

import (
	"strings"
	"unicode"
)

func Strip(value string) string {
	return strings.TrimSpace(value)
}

func NormalizeWhitespace(value string) string {
	var builder strings.Builder
	builder.Grow(len(value))

	runesValue := []rune(value)
	previousWasWhitespace := false
	for index := 0; index < len(runesValue); index++ {
		runeValue := runesValue[index]
		isWhitespace := unicode.IsSpace(runeValue)
		if isWhitespace {
			if builder.Len() > 0 {
				previousWasWhitespace = true
			}
			continue
		}

		if previousWasWhitespace {
			builder.WriteRune(' ')
			previousWasWhitespace = false
		}

		builder.WriteRune(runeValue)
	}

	return strings.TrimSpace(builder.String())
}

func ToLower(value string) string {
	return strings.ToLower(value)
}

func AppendMarker(value string) string {
	return value + "|"
}
