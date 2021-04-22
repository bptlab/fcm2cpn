package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

import java.io.PrintStream;

public abstract class ValidationMessage extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public ValidationMessage(String message) {
		super(message);
	}
	
	private void printTo(PrintStream stream) {
		stream.println("["+severity()+"] "+getMessage()+"\n\t"+getClass().getSimpleName()+" at "+getStackTrace()[0]);
	}
	
	public void printToSysErr() {
		printTo(System.err);
	}
	
	protected abstract String severity();
	
	
	protected boolean isError() {
		return false;
	}
	
}
