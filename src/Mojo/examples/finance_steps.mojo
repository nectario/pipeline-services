from python import PythonObject

struct FinanceSteps:
    @staticmethod
    fn compute_features(tick_value: PythonObject) -> PythonObject:
        var symbol: String = String(tick_value.get("symbol", ""))
        var price: Float64 = Float64(tick_value.get("price", 0.0))
        var vol: Float64 = abs(price) * 0.01
        return Python.dict(r1=0.0, vol=vol, symbol=symbol, price=price)

    @staticmethod
    fn score(features_value: PythonObject) -> PythonObject:
        var vol: Float64 = Float64(features_value.get("vol", 0.0))
        var score_value_float: Float64 = max(0.0, 1.0 - vol)
        return Python.dict(value=score_value_float)

    @staticmethod
    fn decide(score_value: PythonObject) -> PythonObject:
        var val: Float64 = Float64(score_value.get("value", 0.0))
        if val >= 0.5:
            return Python.dict(type="accept", symbol="AAPL", qty=10, price=101.25)
        else:
            return Python.dict(type="reject", reason="LowScore")
