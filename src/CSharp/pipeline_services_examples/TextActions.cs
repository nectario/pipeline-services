using System;
using System.Text.RegularExpressions;

namespace PipelineServices.Examples;

public static class TextActions
{
    private static readonly Regex whitespaceRegex = new Regex("\\s+", RegexOptions.Compiled);

    public static string Strip(string value)
    {
        if (value == null)
        {
            throw new ArgumentNullException(nameof(value));
        }
        return value.Trim();
    }

    public static string NormalizeWhitespace(string value)
    {
        if (value == null)
        {
            throw new ArgumentNullException(nameof(value));
        }
        string trimmed = value.Trim();
        return whitespaceRegex.Replace(trimmed, " ");
    }

    public static string ToLower(string value)
    {
        if (value == null)
        {
            throw new ArgumentNullException(nameof(value));
        }
        return value.ToLowerInvariant();
    }

    public static string AppendMarker(string value)
    {
        if (value == null)
        {
            throw new ArgumentNullException(nameof(value));
        }
        return value + "|";
    }

    public static string Upper(string value)
    {
        if (value == null)
        {
            throw new ArgumentNullException(nameof(value));
        }
        return value.ToUpperInvariant();
    }

    public static string TruncateAt280(string value, PipelineServices.Core.StepControl<string> control)
    {
        if (value == null)
        {
            throw new ArgumentNullException(nameof(value));
        }
        if (control == null)
        {
            throw new ArgumentNullException(nameof(control));
        }

        if (value.Length <= 280)
        {
            return value;
        }

        control.ShortCircuit();
        return value.Substring(0, 280);
    }
}
