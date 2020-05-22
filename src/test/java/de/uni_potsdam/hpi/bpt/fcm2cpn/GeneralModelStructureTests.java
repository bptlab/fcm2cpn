package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.cpntools.accesscpn.model.Page;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;

@ModelsToTest({"Simple", "SimpleWithStates", "TranslationJob"})
public class GeneralModelStructureTests extends ModelStructureTests {
	
	@TestWithAllModels
	public void testNetIsSound(String model) throws Exception {
		checkNet();
	}
	
	@TestWithAllModels
	public void testFirstPageIsMainPage(String model) {
		Page mainPage = petrinet.getPage().get(0);
		assertEquals("Main Page", mainPage.getName().asString());
	}
	
	@TestWithAllModels
	public void testStartEventTransitionIsCreated(String model) {
		forEach(StartEvent.class, startEvent -> 
			assertEquals(1, instancesNamed(startEvent.getName()).count(), 
					"There is not exactly one sub page transition for start event "+startEvent.getName())
		);
	}
	
	@TestWithAllModels
	public void testActivityTransitionIsCreated(String model) {
		forEach(Activity.class, activity -> 
		assertEquals(1, instancesNamed(activity.getName()).count(), 
				"There is not exactly one sub page transition for activity "+activity.getName())
		);
	}
		
	@TestWithAllModels
	public void testStartEventSubPageIsCreated(String model) {
		forEach(StartEvent.class, startEvent -> 
			assertEquals(1, pagesNamed(CompilerApp.normalizeElementName(startEvent.getName())).count(), 
					"There is not exactly one sub page for start event "+startEvent.getName())
		);
	}
	
	@TestWithAllModels
	public void testActivitySubPageIsCreated(String model) {
		forEach(Activity.class, activity -> 
			assertEquals(1, pagesNamed(CompilerApp.normalizeElementName(activity.getName())).count(), 
					"There is not exactly one sub page for activity "+activity.getName())
			);
	}

//TODO: There is not necessarily one node created for each bpmn element (see Gateway). So this is not a valid invariant!
//	@TestWithAllModels
//	public void testControlFlowPlaceIsCreated(String model) {
//		forEach(SequenceFlow.class, sequenceFlow -> 
//			assertEquals(1, controlFlowPlacesBetween(sequenceFlow.getSource().getName(), sequenceFlow.getTarget().getName()).count(), 
//				"There is not exactly one place for the control flow between "+sequenceFlow.getSource().getName()+" and "+sequenceFlow.getTarget().getName())		
//		);
//	}
	
	@TestWithAllModels
	public void testDataObjectPlacesAreCreated(String model) {
		forEach(DataObject.class, dataObject -> {
			assertEquals(1, dataObjectPlacesNamed(CompilerApp.normalizeElementName(dataObject.getName())).count(), 
				"There is not exactly one place for data object "+dataObject.getName());
		});
	}

}
