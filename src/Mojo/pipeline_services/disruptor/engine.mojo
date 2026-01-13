from python import PythonObject

struct DisruptorEngine:
    var name: String

    fn __init__(out self, name: String):
        self.name = name

    fn publish(self, value: PythonObject) raises -> None:
        raise "DisruptorEngine is not implemented in this Mojo port yet"

