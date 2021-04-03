package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
	
	public static Collector<TerminationLiteral, ?, Map<String, List<String>>> stateMapCollector() {
		return Collectors.toMap(TerminationLiteral::getDataObject, literal -> new ArrayList<>(literal.getStates()));
	}

}
