using System;
using System.Text;

using PipelineServices.Core;

namespace PipelineServices.Generated;

public static class PromptGeneratedActions
{
    public static void Register(PipelineRegistry<string> registry)
    {
        if (registry == null)
        {
            throw new ArgumentNullException(nameof(registry));
        }
        registry.RegisterUnary("prompt:normalize_name", NormalizeNameAction);
    }

    public static string NormalizeNameAction(string textValue)
    {
        string outputValue = textValue;
        outputValue = CollapseWhitespace(outputValue);
        outputValue = outputValue.Trim();
        outputValue = TitleCaseTokens(outputValue);
        return outputValue;
    }

    private static string CollapseWhitespace(string textValue)
    {
        StringBuilder builder = new StringBuilder(textValue.Length);
        bool previousWasSpace = false;
        foreach (char characterValue in textValue)
        {
            if (char.IsWhiteSpace(characterValue))
            {
                if (!previousWasSpace)
                {
                    builder.Append(' ');
                    previousWasSpace = true;
                }
                continue;
            }
            previousWasSpace = false;
            builder.Append(characterValue);
        }
        return builder.ToString();
    }

    private static string RemoveHtmlTags(string textValue)
    {
        StringBuilder builder = new StringBuilder(textValue.Length);
        bool insideTag = false;
        foreach (char characterValue in textValue)
        {
            if (characterValue == '<')
            {
                insideTag = true;
                continue;
            }
            if (insideTag)
            {
                if (characterValue == '>')
                {
                    insideTag = false;
                }
                continue;
            }
            builder.Append(characterValue);
        }
        return builder.ToString();
    }

    private static string TitleCaseTokens(string textValue)
    {
        string[] tokens = textValue.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        StringBuilder builder = new StringBuilder(textValue.Length);
        foreach (string token in tokens)
        {
            if (builder.Length > 0)
            {
                builder.Append(' ');
            }
            string lowerToken = token.ToLowerInvariant();
            builder.Append(char.ToUpperInvariant(lowerToken[0]));
            if (lowerToken.Length > 1)
            {
                builder.Append(lowerToken.Substring(1));
            }
        }
        return builder.ToString();
    }

}
