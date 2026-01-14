pub struct DisruptorEngine {
  pub name: String,
}

impl DisruptorEngine {
  pub fn new(name: impl Into<String>) -> Self {
    Self { name: name.into() }
  }

  pub fn publish<ValueType>(&self, value: ValueType) -> Result<(), String> {
    drop(value);
    Err("DisruptorEngine is not implemented in this Rust port yet".to_string())
  }
}

