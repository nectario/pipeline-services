using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

using PipelineServices.Core;

namespace PipelineServices.Remote;

public static class HttpStep
{
    public static StepAction<ContextType> JsonPost<ContextType>(RemoteSpec<ContextType> remoteSpec)
    {
        return new HttpJsonAction<ContextType>(remoteSpec, "POST");
    }

    public static StepAction<ContextType> JsonGet<ContextType>(RemoteSpec<ContextType> remoteSpec)
    {
        return new HttpJsonAction<ContextType>(remoteSpec, "GET");
    }

    public static Func<InputType, OutputType> JsonPostTyped<InputType, OutputType>(RemoteSpecTyped<InputType, OutputType> remoteSpec)
    {
        TypedInvoker<InputType, OutputType> invoker = new TypedInvoker<InputType, OutputType>(remoteSpec, "POST");
        return invoker.Apply;
    }

    public static Func<InputType, OutputType> JsonGetTyped<InputType, OutputType>(RemoteSpecTyped<InputType, OutputType> remoteSpec)
    {
        TypedInvoker<InputType, OutputType> invoker = new TypedInvoker<InputType, OutputType>(remoteSpec, "GET");
        return invoker.Apply;
    }

    private static string WithQuery(string endpoint, string query)
    {
        if (string.IsNullOrWhiteSpace(query))
        {
            return endpoint;
        }
        if (endpoint.Contains("?", StringComparison.Ordinal))
        {
            return endpoint + "&" + query;
        }
        return endpoint + "?" + query;
    }

    private static void ValidateRemoteSpec<ContextType>(RemoteSpec<ContextType> remoteSpec)
    {
        if (remoteSpec == null)
        {
            throw new ArgumentNullException(nameof(remoteSpec));
        }
        if (string.IsNullOrWhiteSpace(remoteSpec.Endpoint))
        {
            throw new ArgumentException("RemoteSpec.Endpoint is required", nameof(remoteSpec));
        }
        if (remoteSpec.ToJson == null)
        {
            throw new ArgumentException("RemoteSpec.ToJson is required", nameof(remoteSpec));
        }
        if (remoteSpec.FromJson == null)
        {
            throw new ArgumentException("RemoteSpec.FromJson is required", nameof(remoteSpec));
        }
        if (remoteSpec.TimeoutMillis < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(remoteSpec), "RemoteSpec.TimeoutMillis must be >= 1");
        }
        if (remoteSpec.Retries < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(remoteSpec), "RemoteSpec.Retries must be >= 0");
        }
    }

    private static void ValidateRemoteSpecTyped<InputType, OutputType>(RemoteSpecTyped<InputType, OutputType> remoteSpec)
    {
        if (remoteSpec == null)
        {
            throw new ArgumentNullException(nameof(remoteSpec));
        }
        if (string.IsNullOrWhiteSpace(remoteSpec.Endpoint))
        {
            throw new ArgumentException("RemoteSpecTyped.Endpoint is required", nameof(remoteSpec));
        }
        if (remoteSpec.ToJson == null)
        {
            throw new ArgumentException("RemoteSpecTyped.ToJson is required", nameof(remoteSpec));
        }
        if (remoteSpec.FromJson == null)
        {
            throw new ArgumentException("RemoteSpecTyped.FromJson is required", nameof(remoteSpec));
        }
        if (remoteSpec.TimeoutMillis < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(remoteSpec), "RemoteSpecTyped.TimeoutMillis must be >= 1");
        }
        if (remoteSpec.Retries < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(remoteSpec), "RemoteSpecTyped.Retries must be >= 0");
        }
    }

    private static ContextType Invoke<ContextType>(RemoteSpec<ContextType> remoteSpec, string method, ContextType contextValue)
    {
        ValidateRemoteSpec(remoteSpec);

        HttpClient httpClient = remoteSpec.Client ?? new HttpClient();
        Func<ContextType, string> toJson = remoteSpec.ToJson ?? throw new InvalidOperationException("RemoteSpec.ToJson is required");
        Func<ContextType, string, ContextType> fromJson =
            remoteSpec.FromJson ?? throw new InvalidOperationException("RemoteSpec.FromJson is required");

        string requestBody = toJson(contextValue) ?? "";

        Exception? lastException = null;
        for (int attemptIndex = 0; attemptIndex <= remoteSpec.Retries; attemptIndex++)
        {
            try
            {
                Uri endpoint = new Uri(
                    string.Equals(method, "POST", StringComparison.OrdinalIgnoreCase)
                        ? remoteSpec.Endpoint
                        : WithQuery(remoteSpec.Endpoint, requestBody));

                using HttpRequestMessage request = new HttpRequestMessage(
                    string.Equals(method, "GET", StringComparison.OrdinalIgnoreCase) ? HttpMethod.Get : HttpMethod.Post,
                    endpoint);

                if (string.Equals(method, "POST", StringComparison.OrdinalIgnoreCase))
                {
                    request.Content = new StringContent(requestBody, Encoding.UTF8, "application/json");
                }

                IDictionary<string, string> headers = remoteSpec.Headers ?? new Dictionary<string, string>(StringComparer.Ordinal);
                foreach (KeyValuePair<string, string> header in headers)
                {
                    request.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
                request.Headers.TryAddWithoutValidation("Content-Type", "application/json");

                using CancellationTokenSource cancellationTokenSource =
                    new CancellationTokenSource(TimeSpan.FromMilliseconds(remoteSpec.TimeoutMillis));

                using HttpResponseMessage response = httpClient.Send(request, cancellationTokenSource.Token);
                string responseBody = response.Content.ReadAsStringAsync(cancellationTokenSource.Token).GetAwaiter().GetResult();

                int statusCode = (int)response.StatusCode;
                if (statusCode >= 200 && statusCode < 300)
                {
                    ContextType next = fromJson(contextValue, responseBody);
                    if (next is null)
                    {
                        throw new InvalidOperationException("RemoteSpec.FromJson returned null");
                    }
                    return next;
                }

                lastException = new HttpRequestException("HTTP " + statusCode + " body=" + responseBody);
            }
            catch (Exception exception) when (exception is HttpRequestException || exception is TaskCanceledException || exception is OperationCanceledException)
            {
                lastException = exception;
            }
        }

        if (lastException != null)
        {
            throw lastException;
        }
        throw new HttpRequestException("Unknown HTTP error");
    }

    private static OutputType InvokeTyped<InputType, OutputType>(RemoteSpecTyped<InputType, OutputType> remoteSpec, string method, InputType inputValue)
    {
        ValidateRemoteSpecTyped(remoteSpec);

        HttpClient httpClient = remoteSpec.Client ?? new HttpClient();
        Func<InputType, string> toJson = remoteSpec.ToJson ?? throw new InvalidOperationException("RemoteSpecTyped.ToJson is required");
        Func<string, OutputType> fromJson =
            remoteSpec.FromJson ?? throw new InvalidOperationException("RemoteSpecTyped.FromJson is required");

        string requestBody = toJson(inputValue) ?? "";

        Exception? lastException = null;
        for (int attemptIndex = 0; attemptIndex <= remoteSpec.Retries; attemptIndex++)
        {
            try
            {
                Uri endpoint = new Uri(
                    string.Equals(method, "POST", StringComparison.OrdinalIgnoreCase)
                        ? remoteSpec.Endpoint
                        : WithQuery(remoteSpec.Endpoint, requestBody));

                using HttpRequestMessage request = new HttpRequestMessage(
                    string.Equals(method, "GET", StringComparison.OrdinalIgnoreCase) ? HttpMethod.Get : HttpMethod.Post,
                    endpoint);

                if (string.Equals(method, "POST", StringComparison.OrdinalIgnoreCase))
                {
                    request.Content = new StringContent(requestBody, Encoding.UTF8, "application/json");
                }

                IDictionary<string, string> headers = remoteSpec.Headers ?? new Dictionary<string, string>(StringComparer.Ordinal);
                foreach (KeyValuePair<string, string> header in headers)
                {
                    request.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
                request.Headers.TryAddWithoutValidation("Content-Type", "application/json");

                using CancellationTokenSource cancellationTokenSource =
                    new CancellationTokenSource(TimeSpan.FromMilliseconds(remoteSpec.TimeoutMillis));

                using HttpResponseMessage response = httpClient.Send(request, cancellationTokenSource.Token);
                string responseBody = response.Content.ReadAsStringAsync(cancellationTokenSource.Token).GetAwaiter().GetResult();

                int statusCode = (int)response.StatusCode;
                if (statusCode >= 200 && statusCode < 300)
                {
                    OutputType next = fromJson(responseBody);
                    if (next is null)
                    {
                        throw new InvalidOperationException("RemoteSpecTyped.FromJson returned null");
                    }
                    return next;
                }

                lastException = new HttpRequestException("HTTP " + statusCode + " body=" + responseBody);
            }
            catch (Exception exception) when (exception is HttpRequestException || exception is TaskCanceledException || exception is OperationCanceledException)
            {
                lastException = exception;
            }
        }

        if (lastException != null)
        {
            throw lastException;
        }
        throw new HttpRequestException("Unknown HTTP error");
    }

    public sealed class RemoteSpec<ContextType>
    {
        public string Endpoint { get; set; } = "";
        public int TimeoutMillis { get; set; } = 1000;
        public int Retries { get; set; } = 0;
        public IDictionary<string, string> Headers { get; set; } = new Dictionary<string, string>(StringComparer.Ordinal);
        public HttpClient Client { get; set; } = new HttpClient();
        public Func<ContextType, string>? ToJson { get; set; }
        public Func<ContextType, string, ContextType>? FromJson { get; set; }
    }

    public sealed class RemoteSpecTyped<InputType, OutputType>
    {
        public string Endpoint { get; set; } = "";
        public int TimeoutMillis { get; set; } = 1000;
        public int Retries { get; set; } = 0;
        public IDictionary<string, string> Headers { get; set; } = new Dictionary<string, string>(StringComparer.Ordinal);
        public HttpClient Client { get; set; } = new HttpClient();
        public Func<InputType, string>? ToJson { get; set; }
        public Func<string, OutputType>? FromJson { get; set; }
    }

    public sealed class RemoteDefaults
    {
        public string BaseUrl { get; set; } = "";
        public int TimeoutMillis { get; set; } = 1000;
        public int Retries { get; set; } = 0;
        public IDictionary<string, string> Headers { get; set; } = new Dictionary<string, string>(StringComparer.Ordinal);
        public string Method { get; set; } = "POST";
        public string? Serde { get; set; }
        public HttpClient Client { get; set; } = new HttpClient();

        public string ResolveEndpoint(string endpointOrPath)
        {
            if (endpointOrPath == null)
            {
                throw new ArgumentNullException(nameof(endpointOrPath));
            }

            string value = endpointOrPath.Trim();
            if (value.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                value.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                return value;
            }

            if (string.IsNullOrWhiteSpace(BaseUrl))
            {
                return value;
            }

            string baseValue = BaseUrl.Trim();
            if (baseValue.EndsWith("/", StringComparison.Ordinal) && value.StartsWith("/", StringComparison.Ordinal))
            {
                return baseValue + value.Substring(1);
            }
            if (!baseValue.EndsWith("/", StringComparison.Ordinal) && !value.StartsWith("/", StringComparison.Ordinal))
            {
                return baseValue + "/" + value;
            }
            return baseValue + value;
        }

        public IDictionary<string, string> MergeHeaders(IDictionary<string, string>? overrides)
        {
            Dictionary<string, string> merged = new Dictionary<string, string>(StringComparer.Ordinal);

            IDictionary<string, string> baseHeaders = Headers ?? new Dictionary<string, string>(StringComparer.Ordinal);
            foreach (KeyValuePair<string, string> header in baseHeaders)
            {
                merged[header.Key] = header.Value;
            }

            if (overrides != null)
            {
                foreach (KeyValuePair<string, string> header in overrides)
                {
                    merged[header.Key] = header.Value;
                }
            }

            return merged;
        }

        public RemoteSpec<ContextType> Spec<ContextType>(
            string endpointOrPath,
            Func<ContextType, string> toJson,
            Func<ContextType, string, ContextType> fromJson)
        {
            RemoteSpec<ContextType> remoteSpec = new RemoteSpec<ContextType>();
            remoteSpec.Endpoint = ResolveEndpoint(endpointOrPath);
            remoteSpec.TimeoutMillis = TimeoutMillis;
            remoteSpec.Retries = Retries;
            remoteSpec.Headers = MergeHeaders(null);
            remoteSpec.Client = Client;
            remoteSpec.ToJson = toJson;
            remoteSpec.FromJson = fromJson;
            return remoteSpec;
        }

        public StepAction<ContextType> Action<ContextType>(
            string endpointOrPath,
            Func<ContextType, string> toJson,
            Func<ContextType, string, ContextType> fromJson)
        {
            RemoteSpec<ContextType> remoteSpec = Spec(endpointOrPath, toJson, fromJson);
            if (string.Equals(Method, "GET", StringComparison.OrdinalIgnoreCase))
            {
                return JsonGet(remoteSpec);
            }
            return JsonPost(remoteSpec);
        }

        public RemoteSpecTyped<InputType, OutputType> TypedSpec<InputType, OutputType>(
            string endpointOrPath,
            Func<InputType, string> toJson,
            Func<string, OutputType> fromJson)
        {
            RemoteSpecTyped<InputType, OutputType> remoteSpec = new RemoteSpecTyped<InputType, OutputType>();
            remoteSpec.Endpoint = ResolveEndpoint(endpointOrPath);
            remoteSpec.TimeoutMillis = TimeoutMillis;
            remoteSpec.Retries = Retries;
            remoteSpec.Headers = MergeHeaders(null);
            remoteSpec.Client = Client;
            remoteSpec.ToJson = toJson;
            remoteSpec.FromJson = fromJson;
            return remoteSpec;
        }

        public Func<InputType, OutputType> CreateFunc<InputType, OutputType>(
            string endpointOrPath,
            Func<InputType, string> toJson,
            Func<string, OutputType> fromJson)
        {
            RemoteSpecTyped<InputType, OutputType> remoteSpec = TypedSpec(endpointOrPath, toJson, fromJson);
            if (string.Equals(Method, "GET", StringComparison.OrdinalIgnoreCase))
            {
                return JsonGetTyped(remoteSpec);
            }
            return JsonPostTyped(remoteSpec);
        }
    }

    private sealed class HttpJsonAction<ContextType> : StepAction<ContextType>
    {
        private readonly RemoteSpec<ContextType> remoteSpec;
        private readonly string method;

        public HttpJsonAction(RemoteSpec<ContextType> remoteSpec, string method)
        {
            this.remoteSpec = remoteSpec ?? throw new ArgumentNullException(nameof(remoteSpec));
            this.method = method ?? throw new ArgumentNullException(nameof(method));
        }

        public ContextType Apply(ContextType contextValue, StepControl<ContextType> control)
        {
            return Invoke(remoteSpec, method, contextValue);
        }
    }

    private sealed class TypedInvoker<InputType, OutputType>
    {
        private readonly RemoteSpecTyped<InputType, OutputType> remoteSpec;
        private readonly string method;

        public TypedInvoker(RemoteSpecTyped<InputType, OutputType> remoteSpec, string method)
        {
            this.remoteSpec = remoteSpec ?? throw new ArgumentNullException(nameof(remoteSpec));
            this.method = method ?? throw new ArgumentNullException(nameof(method));
        }

        public OutputType Apply(InputType inputValue)
        {
            return InvokeTyped(remoteSpec, method, inputValue);
        }
    }
}
