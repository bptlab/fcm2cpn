package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

public class Warning extends ValidationMessage {
	
	public Warning(String message) {
		super(message);
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected String severity() {
		return "Warning";
	}

}
