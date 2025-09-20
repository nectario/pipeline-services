from python import PythonObject

var predicate_state = {"count": 0}

struct TypedPredicates:
    @staticmethod
    fn needs_await(features_value: PythonObject) -> Bool:
        predicate_state["count"] = predicate_state.get("count", 0) + 1
        var is_needed: Bool = Bool(predicate_state["count"] <= 2)
        return is_needed
