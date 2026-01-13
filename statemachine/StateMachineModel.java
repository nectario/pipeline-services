package com.nektron.statemachine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import com.nektron.statemachine.StateMachine.NoArgConsumer;

/**
 * Every state machine has a model which this class represents.
 * 
 * @author nektarios
 *
 * @param <INPUT>
 * @param <STATE>
 */
public class StateMachineModel<INPUT,STATE extends Enum<STATE>,OUTPUT> {
	
	private Map<STATE, StateTransitions<INPUT,STATE,OUTPUT>> stateTransitionMap; 
	private Set<STATE> terminalStates; 
	private Set<STATE> doneStates; 
	private STATE initialState; 
	private String nextPhaseStateMachineId; 
	private String id; 

	public StateMachineModel(String id, STATE initialState) {
		this.id= id; 
		this.initialState = initialState; 
		this.stateTransitionMap = getMap(initialState); 
		this.doneStates = EnumSet.noneOf(initialState.getDeclaringClass()); 
		this.terminalStates = EnumSet.noneOf(initialState.getDeclaringClass()); 
	}
	
	public StateMachineModel(STATE initialState) {
		this.initialState = initialState; 
		this.stateTransitionMap = getMap(initialState); 
		this.doneStates = EnumSet.noneOf(initialState.getDeclaringClass()); 
		this.terminalStates = EnumSet.noneOf(initialState.getDeclaringClass()); 
	}
	
	public StateMachineModel(String id, String nextPhaseStatemachineId, STATE initialState) {
		this.id= id; 
		this.initialState = initialState; 
		this.nextPhaseStateMachineId = nextPhaseStatemachineId; 
		this.stateTransitionMap = getMap(initialState); 
		this.doneStates = EnumSet.noneOf(initialState.getDeclaringClass()); 
		this.terminalStates = EnumSet.noneOf(initialState.getDeclaringClass()); 
	}
	
	/**
	 * 
	 * Returns the state transitions for the given state.
	 * 
	 * @param state
	 * @return
	 */
	public StateTransitions<INPUT,STATE,OUTPUT> getStateTransitions(STATE state) {
		return stateTransitionMap.get(state);
	}
	
	/**
	 * 
	 * Returns true or false if the given state is a terminal state.
	 * 
	 * @param state
	 * @return
	 */
	public boolean isTerminalState(STATE state) {
		return terminalStates.contains(state);
	}
	
	/**
	 * 
	 * Returns true or false if the given state is a done state.
	 * 
	 * @param state
	 * @return
	 */
	public boolean isDoneState(STATE state) {
		return doneStates.contains(state);
	}
	
	public STATE getinitialState() { 
		return initialState; 
	}
	
	/**
	 * The id of the state machine
	 * @return
	 */
	public String getId() {
		return id;
	}
	
	public String toString() { 
		return id; 
	}
	
	public boolean hasNextPhase() {
		return nextPhaseStateMachineId != null;
	}
	
	public String getNextPhaseStateMachineId() {
		return nextPhaseStateMachineId;
	}
	
	public void setNextPhaseStateMachineId(String id) {
		nextPhaseStateMachineId = id;
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, Class<T> event, STATE transitionedState, BiFunction<T,OUTPUT,OUTPUT>... action) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		
		stateTransitions.addTransition(event, transitionedState, action);
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, INPUT event, STATE transitionedState, BiFunction<T,OUTPUT,OUTPUT>... action) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		stateTransitions.addTransition(event, transitionedState, action);		
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, INPUT event, STATE transitionedState) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		stateTransitions.addTransition(event, transitionedState);		
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, Class<T> event, STATE transitionedState, Consumer<T>... action) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		
		stateTransitions.addTransition(event, transitionedState, action);
	}
	
	
	public <T extends INPUT> void addTransition(STATE currentState, Class<T> event, STATE transitionedState) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		
		stateTransitions.addTransition(event, transitionedState);
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, INPUT event, STATE transitionedState, Consumer<T>... action) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		stateTransitions.addTransition(event, transitionedState, action);		
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, Class<T> event, STATE transitionedState, NoArgConsumer... action) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		
		stateTransitions.addTransition(event, transitionedState, action);
	}
	
	public <T extends INPUT> void addTransition(STATE currentState, INPUT event, STATE transitionedState, NoArgConsumer... action) {
		
		StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
		
		if ((stateTransitions = stateTransitionMap.get(currentState)) == null) {
			stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(currentState);
			stateTransitionMap.put(currentState, stateTransitions); 
		} 
		stateTransitions.addTransition(event, transitionedState, action);		
	}
	
	public void addDoneStates(STATE... doneStates) {
		
		for(STATE doneState : doneStates) {
			
			this.doneStates.add(doneState); 
			
			StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null; 
			
			if ((stateTransitions = stateTransitionMap.get(doneState)) == null) {
				stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(doneState); 
				stateTransitionMap.put(doneState, stateTransitions); 	
			}
		}
	}
	
	public void addTerminalStates(STATE... terminalStates) {
		for(STATE terminalState : terminalStates) {
			
			this.terminalStates.add(terminalState); 
			StateTransitions<INPUT,STATE,OUTPUT> stateTransitions = null;
			
			if ((stateTransitions = stateTransitionMap.get(terminalState)) == null) {
				stateTransitions = new StateTransitions<INPUT,STATE,OUTPUT>(terminalState); 
				stateTransitionMap.put(terminalState, stateTransitions); 
			}
		}
	}
	
	private Map<STATE, StateTransitions<INPUT, STATE, OUTPUT>> getMap(STATE state) {
		return new EnumMap<>(state.getDeclaringClass());
	}
}