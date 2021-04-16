package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.addGuardCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.SubpageElement;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.CustomCPNFunctions;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class TerminationConditionCompiler {
	
	public static final String TERMINATION_TRANSITION_NAME = "termination";
	
	private final TerminationCondition terminationCondition;
	private final CompilerApp parent;
	private final SubpageElement subPageElement;
	
	public TerminationConditionCompiler(CompilerApp parent, TerminationCondition terminationCondition) {
		this.parent = parent;
		this.terminationCondition = terminationCondition;
		this.subPageElement = parent.createSubpage(TERMINATION_TRANSITION_NAME);
	}
	
	public void compile() {
		List<Transition> transitions = new ArrayList<>();
		
		for(int clauseIndex = 0; clauseIndex < terminationCondition.getClauses().size(); clauseIndex++) {
			List<TerminationLiteral> clause = terminationCondition.getClauses().get(clauseIndex);
			Map<String, String> stateMap = clause.stream().collect(TerminationLiteral.stateMapCollector());
			Transition clauseTransition = createClauseTransition(stateMap, clauseIndex);
			transitions.add(clauseTransition);
		}
		List<Pair<Association, AssociationEnd>> associationsWithGoalboundedEnds = associationsWithGoalboundedEnds();
		if(!associationsWithGoalboundedEnds.isEmpty()) {
			RefPlace registryRefPlace = subPageElement.refPlaceFor(parent.getRegistryPlace());
			RefPlace assocRefPlace = subPageElement.refPlaceFor(parent.getAssociationsPlace());
            transitions.forEach(transition -> subPageElement.createArc(registryRefPlace, transition, "registry"));
            transitions.forEach(transition -> subPageElement.createArc(transition, registryRefPlace, "registry"));
            transitions.forEach(transition -> subPageElement.createArc(assocRefPlace, transition, "assoc"));
            transitions.forEach(transition -> subPageElement.createArc(transition, assocRefPlace, "assoc"));
            
			for(Pair<Association, AssociationEnd> assocAndEnd : associationsWithGoalboundedEnds) {
				AssociationEnd endWithGoalBound = assocAndEnd.second;
				DataObjectWrapper collectionObject = parent.dataObjectByName(endWithGoalBound.getDataObject());
				AssociationEnd otherEnd = assocAndEnd.first.otherElement(endWithGoalBound); 
				DataObjectWrapper singleObject = parent.dataObjectByName(otherEnd.getDataObject());

	            String guardToGetAllOfSingleObject = singleObject.dataElementList() + " = "+"filter (fn(obj) => #1 (#id obj) = "+singleObject.namePrefix()+") registry";
	            transitions.forEach(transition -> addGuardCondition(transition, guardToGetAllOfSingleObject));
	            
	            String guardToCheckGoalCardinality = CustomCPNFunctions.enforceGoalLowerBoundForAll(
	            		singleObject.dataElementList(), 
	            		collectionObject.namePrefix(), 
	            		endWithGoalBound.getGoalLowerBound());
	            transitions.forEach(transition -> addGuardCondition(transition, guardToCheckGoalCardinality));
			}
		}
		
		// TODO assert that states are matching with process model
	}

	private Transition createClauseTransition(Map<String, String> stateMap, int clauseIndex) {
		Transition transition = subPageElement.createTransition("clause_"+clauseIndex);
		stateMap.forEach((dataObjectName, state) -> {
			DataObjectWrapper dataObject = parent.dataObjectByName(dataObjectName);
            Place dataObjectStatePlace = dataObject.getPlace(state);
            parent.createArc(dataObjectStatePlace, subPageElement.getMainPageTransition());
	    	subPageElement.createArc(subPageElement.refPlaceFor(dataObjectStatePlace), transition, dataObject.dataObjectToken());
		});
		subPageElement.createArc(transition, subPageElement.refPlaceFor(parent.getTerminatedCasesPlace()), parent.caseId());
		return transition;
	}
	
	private List<Pair<Association, AssociationEnd>> associationsWithGoalboundedEnds() {
		return parent.getDataModel().getAssociations().stream()
			.flatMap(assoc -> assoc.stream().map(end -> new Pair<>(assoc, end)))
			.filter(assocAndEnd -> assocAndEnd.second.getGoalLowerBound() > 0)
			.collect(Collectors.toList());
	}
}
