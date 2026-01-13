from python import PythonObject

struct PromptStep:
    var prompt_spec: PythonObject

    fn __init__(out self, prompt_spec: PythonObject):
        self.prompt_spec = prompt_spec

    fn run(self, input_value: PythonObject, adapter: PythonObject) raises -> PythonObject:
        if adapter is None:
            raise "No prompt adapter provided"
        return adapter(input_value, self.prompt_spec)

