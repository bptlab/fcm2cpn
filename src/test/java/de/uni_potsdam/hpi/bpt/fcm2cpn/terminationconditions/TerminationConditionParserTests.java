package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.*;

import org.junit.jupiter.api.Test;


public class TerminationConditionParserTests {
	
	private final TerminationCondition parsedCondition = TerminationConditionParser.parse(new File("./src/test/resources/terminationConditionParserTests.json"));
	
	@Test
	public void testClausesAreParsed() {
		assertEquals(2, parsedCondition.getClauses().size(), 
				"The parser did not parse the expected number of clauses");
	}
	
	@Test
	public void testLiteralsAreParsed() {
		assertEquals(2, getClause(0).size(),
				"The parser did not parse the expected number of literals in the first clause");
		
		assertEquals(3, getClause(1).size(),
				"The parser did not parse the expected number of literals in the second clause");
	}
	
	@Test
	public void testDataObjectsAreParsed() {
		assertArrayEquals(new String[] {normalizeElementName("A"), normalizeElementName("b")}, getClause(0).stream().map(TerminationLiteral::getDataObject).toArray(),
				"The parser did not parse the expected data objects");
	}
	
	@Test
	public void testStatesAreParsed() {
		assertIterableEquals(Arrays.asList(singleDataObjectStateToNetColor("state_Z")), getClause(1).get(0).getStates(),
				"The parser did not parse the expected states");
		
		assertIterableEquals(Arrays.asList(singleDataObjectStateToNetColor("state_U"), singleDataObjectStateToNetColor("state_K")), getClause(1).get(1).getStates(),
				"The parser did not parse the expected states");
		
		assertIterableEquals(Arrays.asList(singleDataObjectStateToNetColor("x\ny")), getClause(1).get(2).getStates(),
				"The parser did not parse the expected states");
	}
	
	
	private List<TerminationLiteral> getClause(int index) {
		return parsedCondition.getClauses().get(index);
	}

}
