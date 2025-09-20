from python import Python, PythonObject
from ..core.pipeline import Pipeline
from ..disruptor.engine import DisruptorEngine

fn enrich(event_value: PythonObject) -> PythonObject:
    return event_value

fn alert(event_value: PythonObject) -> PythonObject:
    var symbol: String = String(event_value.get("symbol", ""))
    var price: Float64 = Float64(event_value.get("price", 0.0))
    return "ALERT " + symbol + " " + String(price)

fn main():
    var pipeline = Pipeline.builder("alerts")
    pipeline.add_steps(enrich).add_steps(alert)
    var engine = DisruptorEngine("alerts", pipeline.run)
    try:
        var python_runtime = Python
        var time_module = python_runtime.import_module("time")
        var random_module = python_runtime.import_module("random")
        var price_value: Float64 = 100.0
        var iteration_count: Int = 0
        while iteration_count < 300:
            price_value = price_value + (Float64(random_module.random()) - 0.5) * 2.0
            if price_value < 1.0: price_value = 1.0
            var event_value = Python.dict(symbol="AAPL", price=price_value, ts_ns=time_module.time_ns())
            engine.publish(event_value)
            time_module.sleep(0.005)
            iteration_count = iteration_count + 1
    finally:
        engine.shutdown()
