from python import Python
from python import PythonObject

fn strip(text_value: PythonObject) raises -> PythonObject:
    var text_string: String = String(text_value)
    var stripped_slice = text_string.strip()
    return PythonObject(String(stripped_slice))

fn normalize_whitespace(text_value: PythonObject) raises -> PythonObject:
    var text_string: String = String(text_value)
    var python_re_module = Python.import_module("re")
    var normalized_value = python_re_module.sub("\\\\s+", " ", text_string).strip()
    return PythonObject(String(normalized_value))

fn to_lower(text_value: PythonObject) raises -> PythonObject:
    var text_string: String = String(text_value)
    return PythonObject(String(text_string.lower()))

fn append_marker(text_value: PythonObject) raises -> PythonObject:
    var text_string: String = String(text_value)
    return PythonObject(text_string + "|")
