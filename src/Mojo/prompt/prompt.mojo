from python import PythonObject, Python

struct PromptSpec:
    var name: String
    var goal: String
    var p50_micros: Int
    var rules: PythonObject
    var examples: PythonObject

    fn __init__(out self):
        self.name = "promptStep"
        self.goal = ""
        self.p50_micros = 0
        self.rules = Python.list()
        self.examples = Python.list()

struct PromptBuilder:
    var spec: PromptSpec

    fn __init__(out self):
        self.spec = PromptSpec()

    fn name(self, name: String) -> Self:
        self.spec.name = name; return self

    fn goal(self, goal: String) -> Self:
        self.spec.goal = goal; return self

    fn rule(self, rule_text: String) -> Self:
        self.spec.rules.append(rule_text); return self

    fn example(self, input_value: PythonObject, output_value: PythonObject) -> Self:
        self.spec.examples.append(Python.dict(in=input_value, out=output_value)); return self

    fn p50_micros(self, micros: Int) -> Self:
        self.spec.p50_micros = micros; return self

    fn build(self, adapter: PythonObject = None) -> fn(input_value: PythonObject) raises -> PythonObject:
        var spec_dict = Python.dict(
            name=self.spec.name,
            goal=self.spec.goal,
            p50Micros=self.spec.p50_micros,
            rules=self.spec.rules,
            examples=self.spec.examples
        )
        if adapter is None:
            fn not_configured(input_value: PythonObject) raises -> PythonObject:
                raise "Prompt-generated code not available; provide an adapter to 'build()'"
            return not_configured
        fn call_adapter(input_value: PythonObject) raises -> PythonObject:
            return adapter(input_value, spec_dict)
        return call_adapter
