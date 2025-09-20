from python import PythonObject

fn ignore_errors(step: fn(input_value: PythonObject) raises -> PythonObject) -> fn(input_value: PythonObject) capturing -> PythonObject:
    fn wrapper(input_value: PythonObject) -> PythonObject:
        try:
            var result: PythonObject = step(input_value)
            return result
        except caught_error:
            return input_value
    return wrapper

fn with_fallback(step: fn(input_value: PythonObject) raises -> PythonObject,
                 fallback: fn(error_message: String) -> PythonObject) -> fn(input_value: PythonObject) capturing -> PythonObject:
    fn wrapper(input_value: PythonObject) -> PythonObject:
        try:
            var result: PythonObject = step(input_value)
            return result
        except caught_error:
            var message: String = String(caught_error)
            var alt_value: PythonObject = fallback(message)
            return alt_value
    return wrapper
