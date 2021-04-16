package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestUtils.assertExactlyOne;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.junit.jupiter.api.Test;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class ExplicitModelStructureTests extends ModelStructureTests {

	private static final String ACTIVITYNAME = normalizeElementName("One");
	private static final String STARTEVENTNAME = normalizeElementName("Case\nStarted");
	private static final String FIRSTDATAOBJECTNAME = normalizeElementName("A");
	private static final String SECONDDATAOBJECTNAME = normalizeElementName("B");

	@Test
	public void testAlwaysTrue() {
		assertEquals(6 * 7, 42);
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testNetIsSound() throws Exception {
		checkNet();
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testFirstPageIsMainPage() {
		Page mainPage = petrinet.getPage().get(0);
		assertEquals("Main_Page", mainPage.getName().asString());
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testStartEventTransitionIsCreated() {
		assertExactlyOne(instancesNamed(STARTEVENTNAME), 
				"There is not exactly one sub page transition for the start event");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testActivityTransitionIsCreated() {
		assertExactlyOne(instancesNamed(ACTIVITYNAME), 
				"There is not exactly one sub page transition for the activity");
	}
		
	@Test
	@ModelsToTest("Simple")
	public void testStartEventSubPageIsCreated() {
		assertExactlyOne(pagesNamed(normalizeElementName(STARTEVENTNAME)), 
				"There is not exactly one sub page for the start event");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testActivitySubPageIsCreated() {
		assertExactlyOne(pagesNamed(normalizeElementName(ACTIVITYNAME)), 
				"There is not exactly one sub page for the activity");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testControlFlowPlaceIsCreated() {
		assertExactlyOne(controlFlowPlacesBetween(STARTEVENTNAME, ACTIVITYNAME), 
				"There is not exactly one place for the control flow between start event and activity");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testDataObjectPlacesAreCreated() {
		Arrays.asList(FIRSTDATAOBJECTNAME, SECONDDATAOBJECTNAME).forEach(dataObjectReference -> {
			assertExactlyOne(dataObjectPlaces(normalizeElementName(dataObjectReference), BpmnPreprocessor.BLANK_STATE), 
				"There is not exactly one place for data object reference "+dataObjectReference);
		});
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testReadDataObjectsAreConsumed() {
		Place dataObjectPlace = dataObjectPlaces(normalizeElementName(FIRSTDATAOBJECTNAME), BpmnPreprocessor.BLANK_STATE).findAny().get();
		assertExactlyOne(arcsToNodeNamed(dataObjectPlace, ACTIVITYNAME),
				"There is not exactly one read arc from data object to activity");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testWrittenDataObjectsAreProduced() {
		Place dataObjectPlace = dataObjectPlaces(normalizeElementName(SECONDDATAOBJECTNAME), BpmnPreprocessor.BLANK_STATE).findAny().get();
		assertExactlyOne(arcsFromNodeNamed(dataObjectPlace, ACTIVITYNAME),
				"There is not exactly one write arc from activity to data object");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testStartEventsProduceDataObjects() {
		Page mainPage = petrinet.getPage().get(0);
		Place dataObjectPlace = StreamSupport.stream(mainPage.place().spliterator(), true).filter(place -> {return place.getSort().getText().equals("DATA_OBJECT") && place.getName().asString().equals(Utils.dataPlaceName(normalizeElementName(FIRSTDATAOBJECTNAME), BpmnPreprocessor.BLANK_STATE));}).findAny().get();
		assertExactlyOne(arcsFromNodeNamed(dataObjectPlace, STARTEVENTNAME),
				"There is not exactly one write arc from start event to data object");
	}
	
	@Test
	@ModelsToTest("Simple")
	public void testUnmodifiedDataObjectsArePutBack() {
		Page mainPage = petrinet.getPage().get(0);
		Place dataObjectPlace = StreamSupport.stream(mainPage.place().spliterator(), true).filter(place -> {return place.getSort().getText().equals("DATA_OBJECT") && place.getName().asString().equals(Utils.dataPlaceName(normalizeElementName(FIRSTDATAOBJECTNAME), BpmnPreprocessor.BLANK_STATE));}).findAny().get();
		assertExactlyOne(arcsFromNodeNamed(dataObjectPlace, ACTIVITYNAME),
				"There is not exactly one write back arc from activity to data object");
	}
	
	@Test
	@ModelsToTest("SimpleWithStates")
	public void testInputAndOutputCombinations() {
		List<String> possibleInputStates = Arrays.asList("X", "Y");
		List<String> possibleOutputStates = Arrays.asList("Z1", "Z2", "Z3");
		for(String inputState : possibleInputStates) {
			for(String outputState : possibleOutputStates) {
				assertTrue(transitionForIoCombination(ACTIVITYNAME, FIRSTDATAOBJECTNAME, inputState, SECONDDATAOBJECTNAME, outputState).isPresent(), 
						"There was not exactly one transition for activity "+ACTIVITYNAME+" with input "+inputState+" and output "+outputState);
			}
		}
	}
	
	@Override
	public Stream<String> allModels() {
		return Stream.empty();
	}
}
