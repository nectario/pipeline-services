from std.python import PythonObject


def print_metrics(context: PythonObject) -> PythonObject:
    """Legacy post Action; use run_detailed() for structured timings."""
    print({"context": context})
    return context
