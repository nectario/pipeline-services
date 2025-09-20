class ShortCircuit(Exception):
    def __init__(self, value):
        super().__init__("short_circuit")
        self.value = value

def short_circuit(value):
    raise ShortCircuit(value)
