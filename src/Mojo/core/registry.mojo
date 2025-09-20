from collections.dict import Dict
from python import PythonObject

struct PipelineRegistry:
    var mapping: Dict[String, PythonObject]

    fn __init__(out self):
        self.mapping = Dict[String, PythonObject]()

    fn register(self, key: String, pipeline_callable: PythonObject) -> None:
        self.mapping[key] = pipeline_callable

    fn lookup(self, key: String) -> PythonObject:
        if key in self.mapping: return self.mapping[key]
        else: return PythonObject(None)

    fn as_map(self) -> Dict[String, PythonObject]:
        return self.mapping

    fn size(self) -> Int:
        return Int(len(self.mapping))
