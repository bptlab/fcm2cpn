package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.util.Set;

public class TerminationLiteral {
	
	private final String dataObject;
	private final Set<String> states;
	
	public TerminationLiteral(String dataObject, Set<String> states) {
		this.dataObject = dataObject;
		this.states = states;
	}
	
	@Override
	public String toString() {
		return dataObject+" ["+String.join(" ", states)+"]";
	}

	public String getDataObject() {
		return dataObject;
	}

	public Set<String> getStates() {
		return states;
	}

}
