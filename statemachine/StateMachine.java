package com.nektron.statemachine;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import com.nektron.statemachine.StateMachine.NoArgConsumer;

/**
 * 
 * Interface that all state machines should implement.
 * 
 * @author nektarios
 *
 * @param <INPUT>
 * @param <STATE>
 */
public interface StateMachine<INPUT,STATE extends Enum<STATE>,OUTPUT> {

	public String getId();
	
	public OUTPUT onInput(INPUT event);
	
	public STATE getCurrentState();
	public STATE getPreviousState();
	
	public OUTPUT getValue();
	
	public StateMachineModel<INPUT,STATE,OUTPUT> getStateMachineModel();
	
	public default <T extends INPUT> void addTransition(STATE state, Class<T> event, STATE transitionedState, Consumer<T>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, Class<T> event, STATE transitionedState, Consumer<T> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, T event, STATE transitionedState, Consumer<T>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, T event, STATE transitionedState, Consumer<T> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, Class<T> event, STATE transitionedState, BiConsumer<T,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, Class<T> event, STATE transitionedState, BiConsumer<T,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, T event, STATE transitionedState, BiConsumer<T,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, T event, STATE transitionedState, BiConsumer<T,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, Class<T> event, STATE transitionedState, Function<T,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, Class<T> event, STATE transitionedState, Function<T,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, T event, STATE transitionedState, Function<T,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, T event, STATE transitionedState, Function<T,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, Class<T> event, STATE transitionedState, BiFunction<T,OUTPUT,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, Class<T> event, STATE transitionedState, BiFunction<T,OUTPUT,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, T event, STATE transitionedState, BiFunction<T,OUTPUT,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, T event, STATE transitionedState, BiFunction<T,OUTPUT,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, Class<T> event, STATE transitionedState) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, Class<T> event, STATE transitionedState)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, T event, STATE transitionedState) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, T event, STATE transitionedState)");
	}
	
	public default <T extends INPUT> void addTransition(String nextPhaseStateMachineId, STATE state, T event, STATE transitionedState, Consumer<T>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(String nextPhaseStateMachineId, E state, T event, STATE transitionedState, Consumer<T> action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, Class<T> event, STATE transitionedState, NoArgConsumer... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, Class<T> event, STATE transitionedState, NoArgConsumer action)");
	}
	
	public default <T extends INPUT> void addTransition(STATE state, T event, STATE transitionedState, NoArgConsumer... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTransition(STATE state, T event, STATE transitionedState, NoArgConsumer action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(Class<T> eventType, TriFunction<T,OUTPUT,STATE,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultAction(Class<T> eventType, TriFunction<T,STATE,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(Class<T> eventType, STATE transitionedState, TriFunction<T,OUTPUT,STATE,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultAction(Class<T> eventType, STATE transitionedState, TriFunction<T,STATE,OUTPUT> action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(Class<T> eventType,  Consumer<T>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.aaddDefaultActions(Class<T> eventType,  Consumer<T>... action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(Class<T> eventType, STATE transitionedState,  Consumer<T>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultActions(Class<T> eventType, STATE transitionedState,  Consumer<T>... action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(Class<T> eventType,  NoArgConsumer... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.aaddDefaultActions(Class<T> eventType,  NoArgConsumer... action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(Class<T> eventType, STATE transitionedState,  NoArgConsumer... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultActions(Class<T> eventType, STATE transitionedState,  NoArgConsumer... action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(T event, TriFunction<T,OUTPUT,STATE,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultActions(T event, TriFunction<T,OUTPUT,STATE,OUTPUT>... action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(T event, STATE transitionedState, TriFunction<T,OUTPUT,STATE,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultActions(T event, STATE transitionedState, TriFunction<T,OUTPUT,STATE,OUTPUT>... action) ");
	}
	
	public default <T extends INPUT> void addDefaultActions(T event, Function<OUTPUT,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultActions(T event, Function<OUTPUT,OUTPUT>... action)");
	}
	
	public default <T extends INPUT> void addDefaultActions(T event, STATE transitionedState, Function<OUTPUT,OUTPUT>... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDefaultActions(T event, STATE transitionedState, Function<OUTPUT,OUTPUT>... action) ");
	}
	
	public default <T extends INPUT> void addErrorAction(T event, TriConsumer<T,STATE, ? extends Throwable> action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addErrorAction(T event, TriConsumer<T,E, ? extends Throwable> action)");
	}

	public default <T extends INPUT> void addOperations(Consumer<T>... operation) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addOperations(Consumer<I> operation)");
	}
	
	public default void addOperations(NoArgConsumer... action) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addOperations(NoArgConsumer operation)");
	}
	
	public default <T extends INPUT> void addOperations(BiFunction<T,OUTPUT,OUTPUT>... operation){
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addOperations(addOperation(BiFunction<INPUT,OUTPUT,OUTPUT> opertation)");
	}
	
	public default void addDoneStates(STATE... doneStates) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addDoneStates(E... doneStates)");
	}
	
	public default void addTerminalStates(STATE... terminalStates) {
		throw new IllegalArgumentException("No method implementation added for method: StateMachine.addTerminalStates(E... terminalStates)");
	}
	
	public default boolean isDone() {
		return false;
	}
	
	public default boolean isTerminated() {
		return false;
	}
	
	@FunctionalInterface
	public static interface TriConsumer<T, U, V> {

	    /**
	     * Performs this operation on the given arguments.
	     *
	     * @param t the first input argument
	     * @param u the second input argument
	     * @param u the third input argument
	     */
	    void accept(T t, U u, V v);
	    
	    default TriConsumer<T, U, V> andThen(TriConsumer<? super T, ? super U, ? super V> after) {
	        
	    	Objects.requireNonNull(after);
	        
	        return (l, r, v) -> {
	            accept(l, r, v);
	            after.accept(l, r, v);
	        };
	    }
	}
	
	@FunctionalInterface
	public interface TriFunction<T, U, O, R> {

	    /**
	     * Applies this function to the given arguments.
	     *
	     * @param t the first function argument
	     * @param u the second function argument
	     * @return the function result
	     */
	    R apply(T t, U u, O o);

	    
	    default <V> TriFunction<T, U, O, V> andThen(Function<? super R, ? extends V> after) {
	        Objects.requireNonNull(after);
	        return (T t, U u, O o) -> after.apply(apply(t, u, o));
	    }
	}
	
	@FunctionalInterface
	public static interface NoArgConsumer {
		
	    void appy();
	}
	
	@FunctionalInterface
	public interface NoArgFunction<R> {

	    R apply();
	}
}
