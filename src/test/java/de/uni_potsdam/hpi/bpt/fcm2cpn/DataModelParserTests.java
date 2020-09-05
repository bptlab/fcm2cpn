package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModelParser;

public class DataModelParserTests {
	
	public final DataModel dataModel = DataModelParser.parse(new File("./src/test/resources/testDiagram.uml"));
	public static final Set<String> expectedDataObjects = Stream.of("A", "B", "C", "D").collect(Collectors.toSet());
	public static final Set<Pair<String, String>> expectedAssociations = Stream.of(new Pair<>("A", "B"), new Pair<>("D", "B")).collect(Collectors.toSet());
	
	@Test
	public void testParsedClasses() {
		assertEquals(expectedDataObjects, dataModel.getDataObjects());
	}
	

	@Test
	public void testParsedAssociations() {
		for(String firstObject : expectedDataObjects) {
			for(String secondObject : expectedDataObjects) {
				assertEquals(areExpectedToBeAssociated(firstObject, secondObject), dataModel.isAssociated(firstObject, secondObject), 
						"Assocation between "+firstObject+" and "+secondObject+" is wrong:");
			}
		}
	}
	
	@Test
	public void testParsedMultiplicities() {
		Association abAssoc = dataModel.getAssociation("A", "B").get();
		assertEquals(1, abAssoc.getEnd("A").getLowerBound());
		assertEquals(1, abAssoc.getEnd("A").getUpperBound());
		assertEquals(1, abAssoc.getEnd("B").getLowerBound());
		assertEquals(1, abAssoc.getEnd("B").getUpperBound());
		
		Association dbAssoc = dataModel.getAssociation("D", "B").get();
		assertEquals(3, dbAssoc.getEnd("D").getLowerBound());
		assertEquals(Integer.MAX_VALUE, dbAssoc.getEnd("D").getUpperBound());
		assertEquals(1, dbAssoc.getEnd("B").getLowerBound());
		assertEquals(42, dbAssoc.getEnd("B").getUpperBound());
	}
	
	private static boolean areExpectedToBeAssociated(String firstObject, String secondObject) {
		return expectedAssociations.contains(new Pair<>(firstObject, secondObject)) || expectedAssociations.contains(new Pair<>(secondObject, firstObject));
	}

}
