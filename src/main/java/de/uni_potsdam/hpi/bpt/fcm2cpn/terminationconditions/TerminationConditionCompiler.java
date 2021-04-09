package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.util.List;
import java.util.Map;

import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.SubpageElement;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;

public class TerminationConditionCompiler {
	
	public static final String TERMINATION_PLACE_NAME = "terminated";
	public static final String TERMINATION_TRANSITION_NAME = "Termination";
	
	private final TerminationCondition terminationCondition;
	private final CompilerApp parent;
	private final SubpageElement subPageElement;
	private final Place terminationPlace;
	
	public TerminationConditionCompiler(CompilerApp parent, TerminationCondition terminationCondition) {
		this.parent = parent;
		this.terminationCondition = terminationCondition;
		this.subPageElement = parent.createSubpage(TERMINATION_TRANSITION_NAME);
		this.terminationPlace = parent.createPlace(TERMINATION_PLACE_NAME, "CaseID");
	}
	
	public void compile() {
		for(int clauseIndex = 0; clauseIndex < terminationCondition.getClauses().size(); clauseIndex++) {
			List<TerminationLiteral> clause = terminationCondition.getClauses().get(clauseIndex);
			Map<String, String> stateMap = clause.stream().collect(TerminationLiteral.stateMapCollector());
			createClauseTransition(stateMap, clauseIndex);
		}
		// TODO also ensure goal cardinalities
		// TODO also ensure non-goal lower bounds >= 1 ?
		
		// TODO assert that states are matching with process model
	}

	private void createClauseTransition(Map<String, String> stateMap, int clauseIndex) {
		Transition transition = subPageElement.createTransition("clause_"+clauseIndex);
		stateMap.forEach((dataObjectName, state) -> {
			DataObjectWrapper dataObject = parent.getDataObjects().stream().filter(x -> x.getNormalizedName().equals(dataObjectName)).findAny().get();
            Place dataObjectStatePlace = dataObject.getPlace(state);
            parent.createArc(dataObjectStatePlace, subPageElement.getMainPageTransition());
	    	subPageElement.createArc(subPageElement.refPlaceFor(dataObjectStatePlace), transition, dataObject.dataObjectToken());
		});
		subPageElement.createArc(transition, subPageElement.refPlaceFor(terminationPlace), parent.caseId());
	}
}
