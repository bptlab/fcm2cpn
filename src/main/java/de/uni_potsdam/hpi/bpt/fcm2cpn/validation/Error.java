package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

public class Error extends ValidationMessage {
	
	public Error(String message) {
		super(message);
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected String severity() {
		return "Error";
	}
	
	@Override
	protected boolean isError() {
		return true;
	}

}
