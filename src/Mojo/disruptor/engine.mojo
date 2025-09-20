from python import Python, PythonObject

struct DisruptorEngine:
    var name: String
    var pipeline_callable: PythonObject
    var queue: PythonObject
    var running_event: PythonObject
    var worker_thread: PythonObject

    fn __init__(out self, name: String, pipeline_callable: PythonObject, capacity: Int = 8192):
        self.name = name
        self.pipeline_callable = pipeline_callable
        var python_runtime = Python
        var queue_module = python_runtime.import_module("queue")
        var threading_module = python_runtime.import_module("threading")

        self.queue = queue_module.Queue(maxsize=capacity)
        self.running_event = threading_module.Event()
        self.running_event.set()

        fn worker() -> None:
            while self.running_event.is_set():
                try:
                    var item = self.queue.get(timeout=0.1)
                except caught_error:
                    continue
                try:
                    self.pipeline_callable(item)
                except caught_error:
                    pass
                finally:
                    self.queue.task_done()

        self.worker_thread = threading_module.Thread(target=worker, name=name + "-worker", daemon=True)
        self.worker_thread.start()

    fn publish(self, payload: PythonObject) -> None:
        if not self.running_event.is_set():
            raise "engine stopped"
        # Backpressure: block until space is available
        self.queue.put(payload, block=True)

    fn shutdown(self) -> None:
        self.running_event.clear()

    fn close(self) -> None:
        self.shutdown()
