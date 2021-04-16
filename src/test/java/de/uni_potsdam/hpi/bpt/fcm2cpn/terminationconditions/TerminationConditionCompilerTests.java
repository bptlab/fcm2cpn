package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestUtils.assertExactlyOne;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.CustomCPNFunctions;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class TerminationConditionCompilerTests extends ModelStructureTests {
	
	@TestWithAllModels
	public void testInfrastructureIsCreatedAsNeeded() {
		assertNotNull(getTerminationTransition(),
				"Termination condition transition was not created as needed.");

		assertNotNull(getTerminationPage(),
				"Terminated condition page was not created as needed.");
	}
	
	@TestWithAllModels
	public void testOneTransitionForEachPossibleStateCombination() {
		
		for(List<TerminationLiteral> clause : terminationCondition.get().getClauses()) {
			Map<String, String> stateMap = clause.stream().collect(TerminationLiteral.stateMapCollector());
			assertExactlyOne(getClauseTransitions(), matchesStateCombination(stateMap),
					"There is not exactly one transition for state combination "+stateMap+" of clause "+clause);
		}
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testGoalCardinalitiesAreChecked(DataObject dataObject) {		
		String dataObjectName = elementName(dataObject);
		dataModel.getAssociationsForDataObject(dataObjectName).forEach(assoc -> {
			String otherDataObjectName = assoc.getOtherEnd(dataObjectName).getDataObject();
			int bound = assoc.getEnd(dataObjectName).getGoalLowerBound();
			if(bound > 0) {
				getClauseTransitions().forEach(transition -> {
					assertExactlyOne(guardsOf(transition), CustomCPNFunctions.enforceGoalLowerBoundForAll(otherDataObjectName+"_list", dataObjectName, bound)::equals, 
							"Termination clause transition "+transition.getName().asString()+" does not have exactly one guard that checks that at least "
							+assoc.getEnd(dataObjectName).getGoalLowerBound()+" "+dataObjectName+"(s) are associated with each "+otherDataObjectName);
				});
			}
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testCaseTokenIsMovedFromActiveToTerminated(DataObject dataObject) {
		
		getClauseTransitions().forEach(transition -> {
			assertExactlyOne(arcsFromNodeNamed(transition, CompilerApp.ACTIVE_CASES_PLACE_NAME), 
					"There is not exactly one arc to termination transition "+transition+" to read active cases");
			assertExactlyOne(arcsToNodeNamed(transition, CompilerApp.TERMINATED_CASES_PLACE_NAME), 
					"There is not exactly one arc from termination transition "+transition+" to write terminated cases");
		});
	}
	
	//TODO TestAllTransitionsCreateTokenInTerminationPlace

	
// ======== Infrastructure ==============================================================================================
	
	private Instance getTerminationTransition() {
		return instancesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
	
	private Page getTerminationPage() {
		return pagesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
	
	private Stream<Transition> getClauseTransitions() {
		return StreamSupport.stream(getTerminationPage().transition().spliterator(), false);
	}
	
	private Predicate<Transition> matchesStateCombination(Map<String, String> stateMap) {
		return transition -> {
			return stateMap.entrySet().stream().allMatch(dataObjectAndState -> {
				return transition.getTargetArc().stream().filter(arc -> arc.getSource().getName().asString().equals(Utils.dataPlaceName(dataObjectAndState.getKey(), dataObjectAndState.getValue()))).count() == 1;
			});
		};
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
	
	@Override
	public void compileModel() {
		super.compileModel();
		// Assume that default value is used if no file was provided
		if(terminationCondition.isEmpty()) terminationCondition = Optional.of(TerminationCondition.stateIndependent());
	}
}
