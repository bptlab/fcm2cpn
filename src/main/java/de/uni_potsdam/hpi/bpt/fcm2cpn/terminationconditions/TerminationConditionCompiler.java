package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.SubpageElement;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

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
			Map<String, List<String>> stateMap = clause.stream().collect(Collectors.toMap(TerminationLiteral::getDataObject, literal -> new ArrayList<>(literal.getStates())));
			
			/** All possible state combinations that fulfill this clause*/
			List<Map<String, String>> resolvedLiterals = Utils.indexedCombinationsOf(stateMap);
			int transitionIndex = 0;
			for(Map<String, String> resolvedLiteral : resolvedLiterals) {
				createTransition(resolvedLiteral, clauseIndex, transitionIndex);
				transitionIndex++;
			}
		}
	}

	private void createTransition(Map<String, String> resolvedLiteral, int clauseIndex, int transitionIndex) {
		Transition transition = subPageElement.createTransition("clause_"+clauseIndex+"_t_"+transitionIndex);
		resolvedLiteral.forEach((dataObjectName, state) -> {
			DataObjectWrapper dataObject = parent.getDataObjects().stream().filter(x -> x.getNormalizedName().equals(dataObjectName)).findAny().get();
            Place dataObjectStatePlace = dataObject.getPlace(state);
            parent.createArc(subPageElement.getMainPageTransition(), dataObjectStatePlace);
	    	subPageElement.createArc(transition, subPageElement.refPlaceFor(dataObjectStatePlace), dataObject.dataObjectToken());
		});
		subPageElement.createArc(transition, subPageElement.refPlaceFor(terminationPlace), parent.caseId());
	}
}
