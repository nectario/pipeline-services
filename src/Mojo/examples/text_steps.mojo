from python import PythonObject
from ..core.short_circuit import short_circuit

struct TextSteps:
    @staticmethod
    fn strip(text_value: PythonObject) -> PythonObject:
        if text_value is None: return ""
        var text_string: String = String(text_value)
        return text_string.strip()

    @staticmethod
    fn normalize_whitespace(text_value: PythonObject) -> PythonObject:
        var python_re_module = __import__("re")
        var text_string: String = String(text_value)
        var result = python_re_module.sub("\\s+", " ", text_string)
        return result

    @staticmethod
    fn disallow_emoji(text_value: PythonObject) -> PythonObject:
        var python_re_module = __import__("re")
        var text_string: String = String(text_value)
        var has_emoji = python_re_module.search("[\u2600-\u26FF\u2700-\u27BF]", text_string) is not None
        if has_emoji: raise "Emoji not allowed"
        return text_string

    @staticmethod
    fn truncate_at_280(text_value: PythonObject) raises -> PythonObject:
        if text_value is None: return ""
        var text_string: String = String(text_value)
        if Int(len(text_string)) > 280:
            short_circuit(PythonObject(text_string[:280]))
        return text_string
