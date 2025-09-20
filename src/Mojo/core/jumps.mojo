from python import Python
from python import PythonObject

var python_runtime = Python
var threading_module = python_runtime.import_module("threading")
var thread_local_storage = threading_module.local()

struct ExecutionSignals:
    var signal_kind: String
    var short_value: PythonObject
    var jump_label: String
    var jump_delay_ms: Int

    fn __init__(out self):
        self.signal_kind = "none"
        self.short_value = PythonObject(None)
        self.jump_label = ""
        self.jump_delay_ms = 0

fn reset_signals() -> None:
    thread_local_storage.signal_kind = "none"
    thread_local_storage.short_value = None
    thread_local_storage.jump_label = ""
    thread_local_storage.jump_delay_ms = 0

fn set_short_circuit_value(value: PythonObject) -> None:
    thread_local_storage.signal_kind = "short"
    thread_local_storage.short_value = value

fn set_jump_signal(label: String, delay_ms: Int) -> None:
    thread_local_storage.signal_kind = "jump"
    thread_local_storage.jump_label = label
    thread_local_storage.jump_delay_ms = delay_ms

fn get_current_signals() -> ExecutionSignals:
    var signals = ExecutionSignals()
    var kind_python_value: PythonObject = getattr(thread_local_storage, "signal_kind", "none")
    var short_python_value: PythonObject = getattr(thread_local_storage, "short_value", None)
    var label_python_value: PythonObject = getattr(thread_local_storage, "jump_label", "")
    var delay_python_value: PythonObject = getattr(thread_local_storage, "jump_delay_ms", 0)

    signals.signal_kind = String(kind_python_value)
    signals.short_value = short_python_value
    signals.jump_label = String(label_python_value)
    signals.jump_delay_ms = Int(delay_python_value)
    return signals

fn short_circuit(value: PythonObject) raises -> None:
    set_short_circuit_value(value)
    raise "SHORT_CIRCUIT"

fn jump_now(label: String) raises -> None:
    set_jump_signal(label, 0)
    raise "JUMP_SIGNAL"

fn jump_after(label: String, delay_ms: Int) raises -> None:
    set_jump_signal(label, delay_ms)
    raise "JUMP_SIGNAL"
