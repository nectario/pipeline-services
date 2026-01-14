pub struct PromptStep<PromptSpecType> {
  pub prompt_spec: PromptSpecType,
}

impl<PromptSpecType> PromptStep<PromptSpecType> {
  pub fn new(prompt_spec: PromptSpecType) -> Self {
    Self { prompt_spec }
  }

  pub fn run<InputType, OutputType, AdapterFn>(
    &self,
    input_value: InputType,
    adapter: Option<AdapterFn>,
  ) -> Result<OutputType, String>
  where
    AdapterFn: Fn(InputType, &PromptSpecType) -> OutputType,
  {
    match adapter {
      Some(adapter_function) => Ok(adapter_function(input_value, &self.prompt_spec)),
      None => Err("No prompt adapter provided".to_string()),
    }
  }
}

