package com.nektron.statemachine;

public class KeyWrapper<INPUT> {
		
		private INPUT event;
		private Class<?> classObject;
		
		public KeyWrapper(Class<?> classObject) {
			this.classObject = classObject;
		}
		
		public KeyWrapper(INPUT event) {
			this.event = event;
		}

		@Override
		public int hashCode() {
			if (this.event != null) {
				return event.hashCode();
			} else {
				return classObject.hashCode();
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (this.event != null) {
				KeyWrapper<INPUT> other = (KeyWrapper<INPUT>) obj;
				return event.equals(other.event);
			} else {
				KeyWrapper<INPUT> other = (KeyWrapper<INPUT>) obj;
				return classObject == other.getClassObject();
			}
		}	
		
		public Class<?> getClassObject() {
			return classObject;
		}
}