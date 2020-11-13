package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModelParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class DataModelParserTests {
	
	public final DataModel dataModel = DataModelParser.parse(new File("./src/test/resources/testDiagram.uml"));
	public static final Set<String> expectedDataObjects = Stream.of("a", "b", "c", "d").collect(Collectors.toSet());
	public static final Set<Pair<String, String>> expectedAssociations = Stream.of(new Pair<>("a", "b"), new Pair<>("d", "b")).collect(Collectors.toSet());
	
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
		Association abAssoc = dataModel.getAssociation("a", "b").get();
		assertEquals(1, abAssoc.getEnd("a").getLowerBound());
		assertEquals(1, abAssoc.getEnd("a").getUpperBound());
		assertEquals(1, abAssoc.getEnd("b").getLowerBound());
		assertEquals(1, abAssoc.getEnd("b").getUpperBound());
		
		Association dbAssoc = dataModel.getAssociation("d", "b").get();
		assertEquals(3, dbAssoc.getEnd("d").getLowerBound());
		assertEquals(AssociationEnd.UNLIMITED, dbAssoc.getEnd("d").getUpperBound());
		assertEquals(1, dbAssoc.getEnd("b").getLowerBound());
		assertEquals(42, dbAssoc.getEnd("b").getUpperBound());
	}
	
	private static boolean areExpectedToBeAssociated(String firstObject, String secondObject) {
		return expectedAssociations.contains(new Pair<>(firstObject, secondObject)) || expectedAssociations.contains(new Pair<>(secondObject, firstObject));
	}

}
