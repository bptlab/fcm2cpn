package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;

import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;

import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;

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


	private Place getTerminationPlace() {
		return placesNamed(TerminationConditionCompiler.TERMINATION_PLACE_NAME).findAny().orElse(null);
	}
	
	private Instance getTerminationTransition() {
		return instancesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
	
	private Page getTerminationPage() {
		return pagesNamed(TerminationConditionCompiler.TERMINATION_TRANSITION_NAME).findAny().orElse(null);
	}
}
