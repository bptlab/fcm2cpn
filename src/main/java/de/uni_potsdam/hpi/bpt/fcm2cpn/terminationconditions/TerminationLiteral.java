package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.assumeNameIsNormalized;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class TerminationLiteral {
	
	private final String dataObject;
	private final String state;
	
	public TerminationLiteral(String dataObject, String state) {
		assumeNameIsNormalized(dataObject);
		this.dataObject = dataObject;
		this.state = state;
	}
	
	@Override
	public String toString() {
		return dataObject+" ["+state+"]";
	}

	public String getDataObject() {
		return dataObject;
	}

	public String getState() {
		return state;
	}
	
	public static Collector<TerminationLiteral, ?, Map<String, String>> stateMapCollector() {
		return Collectors.toMap(TerminationLiteral::getDataObject, TerminationLiteral::getState);
	}

}
