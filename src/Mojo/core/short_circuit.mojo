from python import PythonObject
from .jumps import short_circuit as short_circuit_impl

fn short_circuit(value: PythonObject) raises -> None:
    short_circuit_impl(value)
