# Disruptor Stock Alerts Example (pipeline-examples)

This example wires the LMAX **Disruptor** ring buffer to our **Pipeline** to compute stock alerts.

**Pipeline:** `MarketDataEvent -> Enriched -> Alert`

- `PriceSteps.enrich` keeps a last-price map and computes a simple pct change.
- `PriceSteps.alert` maps to an `Alert` with levels: `NONE`, `MEDIUM` (>=1%), `HIGH` (>=3%).

## Run (module-level)

Add this dependency to the `pipeline-examples` module `pom.xml`:

```xml
<dependency>
  <groupId>com.lmax</groupId>
  <artifactId>disruptor</artifactId>
  <version>3.4.4</version>
</dependency>
```

Then:

```bash
./mvnw -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

You should see `Alert[MEDIUM|HIGH ...]` lines for larger price moves.
