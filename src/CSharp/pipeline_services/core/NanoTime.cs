using System;
using System.Diagnostics;

namespace PipelineServices.Core;

internal static class NanoTime
{
    private static readonly long baseTimestamp = Stopwatch.GetTimestamp();
    private static readonly long baseUnixNanos = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() * 1_000_000L;

    public static long GetNowNanos()
    {
        long elapsedTicks = Stopwatch.GetTimestamp() - baseTimestamp;
        long elapsedNanos = (long)((double)elapsedTicks * 1_000_000_000.0 / Stopwatch.Frequency);
        return baseUnixNanos + elapsedNanos;
    }

    public static long GetElapsedNanos(long startTimestamp, long endTimestamp)
    {
        long elapsedTicks = endTimestamp - startTimestamp;
        return (long)((double)elapsedTicks * 1_000_000_000.0 / Stopwatch.Frequency);
    }
}

