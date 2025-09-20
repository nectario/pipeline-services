from pipeline_services.core.pipeline import Pipeline
from pipeline_services.core.short_circuit import short_circuit
from pipeline_services.core.jumps import jump_now

def test_pipeline_basic():
    p = Pipeline("t").add_steps(lambda s: s.strip(), lambda s: s.upper())
    assert p.run("  hi ") == "HI"

def test_short_circuit():
    p = Pipeline("t", short_circuit=True)
    def a(x): return x+1
    def b(x): short_circuit(5)
    def c(x): return x*100
    p.add_steps(a,b,c)
    assert p.run(1) == 5

def test_jump_and_sections():
    p = Pipeline("t")
    def pre(x): return x+1
    def a(x): return x+1
    def j(x): jump_now("a")
    def post(x): return x*10
    p.step(pre, label="preL", section='pre').step(a, label="a", section='main').step(j, label="b", section='main').step(post, label="postL", section='post')
    assert p.run(0) == 2*10  # pre makes 1, a makes 2, jump to a again would loop once, then post *10
