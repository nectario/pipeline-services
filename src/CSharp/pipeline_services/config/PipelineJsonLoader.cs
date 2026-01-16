using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;

using PipelineServices.Core;
using PipelineServices.Remote;

namespace PipelineServices.Config;

public sealed class PipelineJsonLoader
{
    public Pipeline<string> LoadString(string jsonText, PipelineRegistry<string> registry)
    {
        if (jsonText == null)
        {
            throw new ArgumentNullException(nameof(jsonText));
        }
        if (registry == null)
        {
            throw new ArgumentNullException(nameof(registry));
        }

        using JsonDocument document = JsonDocument.Parse(jsonText);
        JsonElement root = document.RootElement;

        string pipelineName = GetRequiredString(root, "pipeline");
        string typeName = GetOptionalString(root, "type", "unary");
        if (!string.Equals(typeName, "unary", StringComparison.Ordinal))
        {
            throw new InvalidOperationException("Only 'unary' pipelines are supported by this loader");
        }

        bool shortCircuitOnException = GetOptionalBoolean(root, "shortCircuitOnException", fallbackPropertyName: "shortCircuit", defaultValue: true);
        Pipeline<string> pipeline = new Pipeline<string>(pipelineName, shortCircuitOnException);

        HttpStep.RemoteDefaults remoteDefaults = ParseRemoteDefaults(root);

        AddSection(root, "pre", pipeline, registry, remoteDefaults);
        if (root.TryGetProperty("actions", out JsonElement actionsElement))
        {
            AddSection(actionsElement, "actions", pipeline, registry, remoteDefaults);
        }
        else if (root.TryGetProperty("steps", out JsonElement stepsElement))
        {
            AddSection(stepsElement, "steps", pipeline, registry, remoteDefaults);
        }
        AddSection(root, "post", pipeline, registry, remoteDefaults);

        return pipeline;
    }

    public Pipeline<string> LoadFile(string filePath, PipelineRegistry<string> registry)
    {
        if (filePath == null)
        {
            throw new ArgumentNullException(nameof(filePath));
        }

        string jsonText = File.ReadAllText(filePath);
        return LoadString(jsonText, registry);
    }

    private static void AddSection(
        JsonElement root,
        string sectionName,
        Pipeline<string> pipeline,
        PipelineRegistry<string> registry,
        HttpStep.RemoteDefaults remoteDefaults)
    {
        if (!root.TryGetProperty(sectionName, out JsonElement sectionElement))
        {
            return;
        }
        AddSection(sectionElement, sectionName, pipeline, registry, remoteDefaults);
    }

    private static void AddSection(
        JsonElement sectionElement,
        string sectionName,
        Pipeline<string> pipeline,
        PipelineRegistry<string> registry,
        HttpStep.RemoteDefaults remoteDefaults)
    {
        if (sectionElement.ValueKind == JsonValueKind.Null || sectionElement.ValueKind == JsonValueKind.Undefined)
        {
            return;
        }
        if (sectionElement.ValueKind != JsonValueKind.Array)
        {
            throw new InvalidOperationException("Section '" + sectionName + "' must be an array");
        }

        foreach (JsonElement actionElement in sectionElement.EnumerateArray())
        {
            if (actionElement.ValueKind != JsonValueKind.Object)
            {
                throw new InvalidOperationException("Each action must be a JSON object");
            }
            AddAction(actionElement, sectionName, pipeline, registry, remoteDefaults);
        }
    }

    private static void AddAction(
        JsonElement actionElement,
        string sectionName,
        Pipeline<string> pipeline,
        PipelineRegistry<string> registry,
        HttpStep.RemoteDefaults remoteDefaults)
    {
        string displayName = GetOptionalString(actionElement, "name", "");
        if (string.IsNullOrWhiteSpace(displayName))
        {
            displayName = GetOptionalString(actionElement, "label", "");
        }

        if (actionElement.TryGetProperty("$local", out JsonElement localElement))
        {
            if (localElement.ValueKind != JsonValueKind.String)
            {
                throw new InvalidOperationException("$local must be a string");
            }
            AddLocal(localElement.GetString() ?? "", displayName, sectionName, pipeline, registry);
            return;
        }

        if (actionElement.TryGetProperty("$remote", out JsonElement remoteElement))
        {
            AddRemote(remoteElement, displayName, sectionName, pipeline, remoteDefaults);
            return;
        }

        throw new InvalidOperationException("Unsupported action: expected '$local' or '$remote'");
    }

    private static void AddLocal(
        string localRef,
        string displayName,
        string sectionName,
        Pipeline<string> pipeline,
        PipelineRegistry<string> registry)
    {
        if (registry.HasUnary(localRef))
        {
            Func<string, string> unaryAction = registry.GetUnary(localRef);
            AddUnary(unaryAction, displayName, sectionName, pipeline);
            return;
        }

        if (registry.HasAction(localRef))
        {
            StepAction<string> action = registry.GetAction(localRef);
            AddStepAction(action, displayName, sectionName, pipeline);
            return;
        }

        throw new InvalidOperationException("Unknown $local reference: " + localRef);
    }

    private static void AddUnary(Func<string, string> unaryAction, string displayName, string sectionName, Pipeline<string> pipeline)
    {
        if (string.Equals(sectionName, "pre", StringComparison.Ordinal))
        {
            pipeline.AddPreAction(displayName, unaryAction);
        }
        else if (string.Equals(sectionName, "post", StringComparison.Ordinal))
        {
            pipeline.AddPostAction(displayName, unaryAction);
        }
        else
        {
            pipeline.AddAction(displayName, unaryAction);
        }
    }

    private static void AddStepAction(StepAction<string> action, string displayName, string sectionName, Pipeline<string> pipeline)
    {
        if (string.Equals(sectionName, "pre", StringComparison.Ordinal))
        {
            pipeline.AddPreAction(displayName, action);
        }
        else if (string.Equals(sectionName, "post", StringComparison.Ordinal))
        {
            pipeline.AddPostAction(displayName, action);
        }
        else
        {
            pipeline.AddAction(displayName, action);
        }
    }

    private static void AddRemote(
        JsonElement remoteElement,
        string displayName,
        string sectionName,
        Pipeline<string> pipeline,
        HttpStep.RemoteDefaults remoteDefaults)
    {
        string method = remoteDefaults.Method;
        HttpStep.RemoteSpec<string> remoteSpec;

        if (remoteElement.ValueKind == JsonValueKind.String)
        {
            string endpointOrPath = remoteElement.GetString() ?? "";
            remoteSpec = remoteDefaults.Spec(endpointOrPath, IdentityToJson, IdentityFromJson);
        }
        else if (remoteElement.ValueKind == JsonValueKind.Object)
        {
            string endpointOrPath = ParseRemoteEndpointOrPath(remoteElement);
            remoteSpec = remoteDefaults.Spec(endpointOrPath, IdentityToJson, IdentityFromJson);

            if (remoteElement.TryGetProperty("timeoutMillis", out JsonElement timeoutElement) &&
                timeoutElement.ValueKind == JsonValueKind.Number)
            {
                remoteSpec.TimeoutMillis = timeoutElement.GetInt32();
            }
            else if (remoteElement.TryGetProperty("timeout_millis", out JsonElement timeoutSnakeElement) &&
                     timeoutSnakeElement.ValueKind == JsonValueKind.Number)
            {
                remoteSpec.TimeoutMillis = timeoutSnakeElement.GetInt32();
            }

            if (remoteElement.TryGetProperty("retries", out JsonElement retriesElement) &&
                retriesElement.ValueKind == JsonValueKind.Number)
            {
                remoteSpec.Retries = retriesElement.GetInt32();
            }

            IDictionary<string, string> headersOverride = ParseHeaders(remoteElement);
            if (headersOverride.Count > 0)
            {
                remoteSpec.Headers = remoteDefaults.MergeHeaders(headersOverride);
            }

            if (remoteElement.TryGetProperty("method", out JsonElement methodElement) &&
                methodElement.ValueKind == JsonValueKind.String)
            {
                method = methodElement.GetString() ?? method;
            }
        }
        else
        {
            throw new InvalidOperationException("$remote must be a string or object");
        }

        StepAction<string> action = string.Equals(method, "GET", StringComparison.OrdinalIgnoreCase)
            ? HttpStep.JsonGet(remoteSpec)
            : HttpStep.JsonPost(remoteSpec);

        AddStepAction(action, displayName, sectionName, pipeline);
    }

    private static string ParseRemoteEndpointOrPath(JsonElement remoteElement)
    {
        if (remoteElement.TryGetProperty("endpoint", out JsonElement endpointElement) &&
            endpointElement.ValueKind == JsonValueKind.String)
        {
            return endpointElement.GetString() ?? "";
        }
        if (remoteElement.TryGetProperty("path", out JsonElement pathElement) &&
            pathElement.ValueKind == JsonValueKind.String)
        {
            return pathElement.GetString() ?? "";
        }

        throw new InvalidOperationException("Missing required $remote field: endpoint|path");
    }

    private static IDictionary<string, string> ParseHeaders(JsonElement remoteElement)
    {
        Dictionary<string, string> headers = new Dictionary<string, string>(StringComparer.Ordinal);
        if (!remoteElement.TryGetProperty("headers", out JsonElement headersElement))
        {
            return headers;
        }
        if (headersElement.ValueKind != JsonValueKind.Object)
        {
            return headers;
        }

        foreach (JsonProperty headerProperty in headersElement.EnumerateObject())
        {
            if (headerProperty.Value.ValueKind == JsonValueKind.String)
            {
                headers[headerProperty.Name] = headerProperty.Value.GetString() ?? "";
            }
        }
        return headers;
    }

    private static HttpStep.RemoteDefaults ParseRemoteDefaults(JsonElement root)
    {
        HttpStep.RemoteDefaults remoteDefaults = new HttpStep.RemoteDefaults();
        if (!root.TryGetProperty("remoteDefaults", out JsonElement defaultsElement))
        {
            return remoteDefaults;
        }
        if (defaultsElement.ValueKind != JsonValueKind.Object)
        {
            return remoteDefaults;
        }

        if (defaultsElement.TryGetProperty("baseUrl", out JsonElement baseUrlElement) &&
            baseUrlElement.ValueKind == JsonValueKind.String)
        {
            remoteDefaults.BaseUrl = baseUrlElement.GetString() ?? "";
        }
        else if (defaultsElement.TryGetProperty("endpointBase", out JsonElement endpointBaseElement) &&
                 endpointBaseElement.ValueKind == JsonValueKind.String)
        {
            remoteDefaults.BaseUrl = endpointBaseElement.GetString() ?? "";
        }

        if (defaultsElement.TryGetProperty("timeoutMillis", out JsonElement timeoutElement) &&
            timeoutElement.ValueKind == JsonValueKind.Number)
        {
            remoteDefaults.TimeoutMillis = timeoutElement.GetInt32();
        }
        else if (defaultsElement.TryGetProperty("timeout_millis", out JsonElement timeoutSnakeElement) &&
                 timeoutSnakeElement.ValueKind == JsonValueKind.Number)
        {
            remoteDefaults.TimeoutMillis = timeoutSnakeElement.GetInt32();
        }

        if (defaultsElement.TryGetProperty("retries", out JsonElement retriesElement) &&
            retriesElement.ValueKind == JsonValueKind.Number)
        {
            remoteDefaults.Retries = retriesElement.GetInt32();
        }

        if (defaultsElement.TryGetProperty("method", out JsonElement methodElement) &&
            methodElement.ValueKind == JsonValueKind.String)
        {
            remoteDefaults.Method = methodElement.GetString() ?? remoteDefaults.Method;
        }

        Dictionary<string, string> baseHeaders = new Dictionary<string, string>(StringComparer.Ordinal);
        if (defaultsElement.TryGetProperty("headers", out JsonElement headersElement) &&
            headersElement.ValueKind == JsonValueKind.Object)
        {
            foreach (JsonProperty headerProperty in headersElement.EnumerateObject())
            {
                if (headerProperty.Value.ValueKind == JsonValueKind.String)
                {
                    baseHeaders[headerProperty.Name] = headerProperty.Value.GetString() ?? "";
                }
            }
        }
        if (baseHeaders.Count > 0)
        {
            remoteDefaults.Headers = remoteDefaults.MergeHeaders(baseHeaders);
        }

        return remoteDefaults;
    }

    private static string GetRequiredString(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out JsonElement valueElement))
        {
            throw new InvalidOperationException("Missing required field: " + propertyName);
        }
        if (valueElement.ValueKind != JsonValueKind.String)
        {
            throw new InvalidOperationException("Field '" + propertyName + "' must be a string");
        }
        return valueElement.GetString() ?? "";
    }

    private static string GetOptionalString(JsonElement root, string propertyName, string defaultValue)
    {
        if (!root.TryGetProperty(propertyName, out JsonElement valueElement))
        {
            return defaultValue;
        }
        if (valueElement.ValueKind != JsonValueKind.String)
        {
            return defaultValue;
        }
        return valueElement.GetString() ?? defaultValue;
    }

    private static bool GetOptionalBoolean(
        JsonElement root,
        string primaryPropertyName,
        string fallbackPropertyName,
        bool defaultValue)
    {
        if (root.TryGetProperty(primaryPropertyName, out JsonElement primaryElement) &&
            primaryElement.ValueKind == JsonValueKind.True)
        {
            return true;
        }
        if (root.TryGetProperty(primaryPropertyName, out primaryElement) &&
            primaryElement.ValueKind == JsonValueKind.False)
        {
            return false;
        }

        if (root.TryGetProperty(fallbackPropertyName, out JsonElement fallbackElement) &&
            fallbackElement.ValueKind == JsonValueKind.True)
        {
            return true;
        }
        if (root.TryGetProperty(fallbackPropertyName, out fallbackElement) &&
            fallbackElement.ValueKind == JsonValueKind.False)
        {
            return false;
        }

        return defaultValue;
    }

    private static string IdentityToJson(string contextValue)
    {
        return contextValue ?? "";
    }

    private static string IdentityFromJson(string contextValue, string body)
    {
        return body;
    }
}

