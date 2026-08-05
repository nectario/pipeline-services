from std.python import Python, PythonObject


def strip(text_value: PythonObject) raises -> PythonObject:
    return PythonObject(String(text_value).strip())


def normalize_whitespace(text_value: PythonObject) raises -> PythonObject:
    var python_re_module = Python.import_module("re")
    var normalized_value = python_re_module.sub(
        "\\s+",
        " ",
        String(text_value),
    ).strip()
    return PythonObject(String(normalized_value))


def to_lower(text_value: PythonObject) raises -> PythonObject:
    return PythonObject(String(text_value).lower())


def append_marker(text_value: PythonObject) raises -> PythonObject:
    return PythonObject(String(text_value) + "|")
