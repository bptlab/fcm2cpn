package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
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
			Map<String, List<String>> stateMap = clause.stream().collect(TerminationLiteral.stateMapCollector());
			List<Map<String, String>> possibleStateCombinationsForClause = Utils.indexedCombinationsOf(stateMap);
			for(Map<String, String> stateCombination : possibleStateCombinationsForClause) {
				List<Transition> transitionsThatMatchStateCombination = StreamSupport.stream(getTerminationPage().transition().spliterator(), false).filter(transition -> {
					return stateCombination.entrySet().stream().allMatch(dataObjectAndState -> {
						return transition.getTargetArc().stream().filter(arc -> arc.getSource().getName().asString().equals(Utils.dataPlaceName(dataObjectAndState.getKey(), dataObjectAndState.getValue()))).count() == 1;
					});
				}).collect(Collectors.toList());
				assertEquals(1, transitionsThatMatchStateCombination.size(),
						"There is not exactly one transition for state combination "+stateCombination+" of clause "+clause);
			}
		}
	}
	
	//TODO TestAllTransitionsCreateTokenInTerminationPlace


	private Place getTerminationPlace() {
		return placesNamed(TerminationConditionCompiler.TERMINATION_PLACE_NAME).findAny().orElse(null);
	}
	
	private Instance getTerminationTransition() {
		return instancesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
	
	private Page getTerminationPage() {
		return pagesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
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
