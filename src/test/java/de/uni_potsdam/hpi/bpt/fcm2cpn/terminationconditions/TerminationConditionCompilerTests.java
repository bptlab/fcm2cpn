package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.CustomCPNFunctions;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class TerminationConditionCompilerTests extends ModelStructureTests {
	
	@TestWithAllModels
	public void testInfrastructureIsCreatedAsNeeded() {
		assertEquals(terminationCondition.isPresent(), Objects.nonNull(getTerminationPlace()),
				"Terminated instances place was not created as needed.");

		assertEquals(terminationCondition.isPresent(), Objects.nonNull(getTerminationTransition()),
				"Termination condition transition was not created as needed.");

		assertEquals(terminationCondition.isPresent(), Objects.nonNull(getTerminationPage()),
				"Terminated condition page was not created as needed.");
	}
	
	@TestWithAllModels
	public void testOneTransitionForEachPossibleStateCombination() {
		assumeTrue(terminationCondition.isPresent(), "Model does not have a termination condition");
		
		for(List<TerminationLiteral> clause : terminationCondition.get().getClauses()) {
			Map<String, String> stateMap = clause.stream().collect(TerminationLiteral.stateMapCollector());
			assertEquals(1, transitionsThatMatchStateCombination(stateMap).size(),
					"There is not exactly one transition for state combination "+stateMap+" of clause "+clause);
		}
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testGoalCardinalitiesAreChecked(DataObject dataObject) {
		assumeTrue(terminationCondition.isPresent(), "Model does not have a termination condition");
		
		String dataObjectName = elementName(dataObject);
		dataModel.getAssociationsForDataObject(dataObjectName).forEach(assoc -> {
			String otherDataObjectName = assoc.getOtherEnd(dataObjectName).getDataObject();
			int bound = assoc.getEnd(dataObjectName).getGoalLowerBound();
			if(bound > 0) {
				getClauseTransitions().forEach(transition -> {
					assertEquals(1, guardsOf(transition).filter(CustomCPNFunctions.enforceGoalLowerBoundForAll(otherDataObjectName+"_list", dataObjectName, bound)::equals).count(), 
							"Termination clause transition "+transition.getName().asString()+" does not have exactly one guard that checks that at least "
							+assoc.getEnd(dataObjectName).getGoalLowerBound()+" "+dataObjectName+"(s) are associated with each "+otherDataObjectName);
				});
			}
		});
	}
	
	//TODO TestAllTransitionsCreateTokenInTerminationPlace

	
// ======== Infrastructure ==============================================================================================
	
	private Place getTerminationPlace() {
		return placesNamed(TerminationConditionCompiler.TERMINATION_PLACE_NAME).findAny().orElse(null);
	}
	
	private Instance getTerminationTransition() {
		return instancesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
	
	private Page getTerminationPage() {
		return pagesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
	
	private Stream<Transition> getClauseTransitions() {
		return StreamSupport.stream(getTerminationPage().transition().spliterator(), false);
	}
	
	private List<Transition> transitionsThatMatchStateCombination(Map<String, String> stateMap) {
		return getClauseTransitions().filter(transition -> {
			return stateMap.entrySet().stream().allMatch(dataObjectAndState -> {
				return transition.getTargetArc().stream().filter(arc -> arc.getSource().getName().asString().equals(Utils.dataPlaceName(dataObjectAndState.getKey(), dataObjectAndState.getValue()))).count() == 1;
			});
		}).collect(Collectors.toList());
	}
	
	@Override
	protected Optional<TerminationCondition> parseTerminationCondition() {
        File terminationConditionFile = new File("./src/test/resources/"+modelName+".json");
        if(terminationConditionFile.exists()) {
        	return Optional.of(TerminationConditionParser.parse(terminationConditionFile));
        } else {
            return super.parseTerminationCondition();
        }
	}
}
