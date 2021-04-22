package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ValidationContext {
	
	private final List<ValidationMessage> messages = new ArrayList<>();
	private final Consumer<ValidationMessage> messageSink = ValidationMessage::printToSysErr;
	
	private boolean throwOnError = true;
	
	public static ValidationContext defaultContext() {
		return new ValidationContext();
	}

	public void handleMessage(ValidationMessage message) {
		messages.add(message);
		messageSink.accept(message);
		if(message.isError() && throwOnError) throw message; 
	}

	public void warn(Warning warning) {
		handleMessage(warning);
	}
	
	public void error(Error error) {
		handleMessage(error);
	}

	public List<ValidationMessage> getMessages() {
		return messages;
	}
	
	public boolean throwsOnError() {
		return throwOnError;
	}

	public void setThrowOnError(boolean throwOnError) {
		this.throwOnError = throwOnError;
	}

}
