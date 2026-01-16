namespace PipelineServices.Core;

public interface StepAction<ContextType>
{
    ContextType Apply(ContextType contextValue, StepControl<ContextType> control);
}

