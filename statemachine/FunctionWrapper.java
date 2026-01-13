package com.nektron.statemachine;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class FunctionWrapper<I,E,O> {

	private Consumer<I> consumer;
	private BiConsumer<I,O> biConsumer;
	private Function<I,O> function;
	private BiFunction<I,O,O> biFunction;
	
	public FunctionWrapper(Function<I,O> function) {
		this.function = function;
	}
	
	public FunctionWrapper(BiFunction<I,O,O> biFunction) {
		this.biFunction = biFunction;
	}
	
	public FunctionWrapper(Consumer<I> consumer) {
		this.consumer = consumer;
	}
	
	public FunctionWrapper(BiConsumer<I,O> biConsumer) {
		this.biConsumer = biConsumer;
	}
}
