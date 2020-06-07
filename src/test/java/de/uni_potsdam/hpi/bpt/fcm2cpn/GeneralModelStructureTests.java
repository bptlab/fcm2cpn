package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.cpntools.accesscpn.model.Page;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;

@ModelsToTest({"Simple", "SimpleWithStates", "TranslationJob"})
public class GeneralModelStructureTests extends ModelStructureTests {
	
	@TestWithAllModels
	public void testNetIsSound() throws Exception {
		checkNet();
	}
	
	@TestWithAllModels
	public void testFirstPageIsMainPage() {
		Page mainPage = petrinet.getPage().get(0);
		assertEquals("Main Page", mainPage.getName().asString());
	}
	
	@TestWithAllModels
	@ForEachBpmn(StartEvent.class)
	public void testStartEventTransitionIsCreated(StartEvent startEvent) {
		assertEquals(1, instancesNamed(startEvent.getName()).count(), 
				"There is not exactly one sub page transition for start event "+startEvent.getName());
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testActivityTransitionIsCreated(Activity activity) {
		assertEquals(1, instancesNamed(activity.getName()).count(), 
				"There is not exactly one sub page transition for activity "+activity.getName());
	}
		
	@TestWithAllModels
	@ForEachBpmn(StartEvent.class)
	public void testStartEventSubPageIsCreated(StartEvent startEvent) {
		assertEquals(1, pagesNamed(CompilerApp.normalizeElementName(startEvent.getName())).count(), 
				"There is not exactly one sub page for start event "+startEvent.getName());
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testActivitySubPageIsCreated(Activity activity) {
		assertEquals(1, pagesNamed(CompilerApp.normalizeElementName(activity.getName())).count(), 
				"There is not exactly one sub page for activity "+activity.getName());
	}

//TODO: There is not necessarily one node created for each bpmn element (see Gateway). So this is not a valid invariant!
//	@TestWithAllModels
//	public void testControlFlowPlaceIsCreated() {
//		forEach(SequenceFlow.class, sequenceFlow -> 
//			assertEquals(1, controlFlowPlacesBetween(sequenceFlow.getSource().getName(), sequenceFlow.getTarget().getName()).count(), 
//				"There is not exactly one place for the control flow between "+sequenceFlow.getSource().getName()+" and "+sequenceFlow.getTarget().getName())		
//		);
//	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testDataObjectPlacesAreCreated(DataObject dataObject) {
		assertEquals(1, dataObjectPlacesNamed(CompilerApp.normalizeElementName(dataObject.getName())).count(), 
			"There is not exactly one place for data object "+dataObject.getName());
	}

}
