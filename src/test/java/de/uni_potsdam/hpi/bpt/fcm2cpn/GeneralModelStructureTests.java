package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp.normalizeElementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp.elementName;

@ModelsToTest({"Simple", "SimpleWithStates", "SimpleWithEvents", "SimpleWithGateways", "TranslationJob"})
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
		assertEquals(1, instancesNamed(elementName(startEvent)).count(), 
				"There is not exactly one sub page transition for start event "+elementName(startEvent));
	}
	
	@TestWithAllModels
	@ForEachBpmn(BoundaryEvent.class)
	public void testBoundaryEventTransitionIsCreated(BoundaryEvent boundaryEvent) {
		assertEquals(1, instancesNamed(elementName(boundaryEvent)).count(), 
				"There is not exactly one sub page transition for boundary event "+elementName(boundaryEvent));
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
		assertEquals(1, pagesNamed(normalizeElementName(elementName(startEvent))).count(), 
				"There is not exactly one sub page for start event "+elementName(startEvent));
	}
	
	@TestWithAllModels
	@ForEachBpmn(BoundaryEvent.class)
	public void testBoundaryEventSubPageIsCreated(BoundaryEvent boundaryEvent) {
		assertEquals(1, pagesNamed(normalizeElementName(elementName(boundaryEvent))).count(), 
				"There is not exactly one sub page for boundary event "+elementName(boundaryEvent));
	}
	
	@TestWithAllModels
	@ForEachBpmn(BoundaryEvent.class)
	public void testBoundaryEventCancelsTaskExecution(BoundaryEvent boundaryEvent) {
		assumeTrue(boundaryEvent.cancelActivity());
		Instance transition = instancesNamed(elementName(boundaryEvent)).findAny().get();
		String canceledActivityName = boundaryEvent.getAttachedTo().getName();
		Place commonCaseTokenPlace = (Place) transition.getTargetArc().get(0).getOtherEnd(transition);
		assertTrue(commonCaseTokenPlace.getSourceArc().stream().map(Arc::getTarget).anyMatch(any -> any.getName().asString().equals(canceledActivityName)),
			"Boundary Event "+elementName(boundaryEvent)+" has no common place to the activity it interrupts: "+canceledActivityName);
	}
	
	@TestWithAllModels
	@ForEachBpmn(EndEvent.class)
	public void testEndEventPlaceIsCreated(EndEvent endEvent) {
		assertEquals(1, placesNamed(elementName(endEvent)).count(), 
				"There is not exactly one place for end event "+elementName(endEvent));
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testActivitySubPageIsCreated(Activity activity) {
		assertEquals(1, pagesNamed(normalizeElementName(activity.getName())).count(), 
				"There is not exactly one sub page for activity "+activity.getName());
	}

	@TestWithAllModels
	@ForEachBpmn(SequenceFlow.class)
	public void testControlFlowPlaceIsCreated(SequenceFlow sequenceFlow) {
		assumeFalse(sequenceFlow.getSource() instanceof Gateway, "The sequence started at a gateway");
		assumeFalse(sequenceFlow.getTarget() instanceof Gateway, "The sequence ended at a gateway");
		assertEquals(1, controlFlowPlacesBetween(sequenceFlow.getSource().getName(), sequenceFlow.getTarget().getName()).count(), 
			"There is not exactly one place for the control flow between "+sequenceFlow.getSource().getName()+" and "+sequenceFlow.getTarget().getName());
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testDataObjectPlacesAreCreated(DataObject dataObject) {
		assertEquals(1, dataObjectPlacesNamed(normalizeElementName(dataObject.getName())).count(), 
			"There is not exactly one place for data object "+dataObject.getName());
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testReadDataObjectsAreConsumed(DataObjectReference dataObjectReference) {
		List<ModelElementInstance> readingNodes = bpmn.getModelElementsByType(DataInputAssociation.class).stream()
			.filter(assoc -> assoc.getSources().contains(dataObjectReference))
			.map(DataInputAssociation::getParentElement)
			.collect(Collectors.toList());
		assumeFalse(readingNodes.isEmpty(), "The data object reference is not read");
		Place dataObjectPlace = dataObjectPlacesNamed(dataObjectReference.getDataObject().getName()).findAny().get();
		
		readingNodes.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsToNodeNamed(dataObjectPlace, nodeName).count(),
					"There is not exactly one read arc from data object reference "+dataObjectReference.getName()+" to node "+nodeName);
		});	
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testWrittenDataObjectsAreProduced(DataObjectReference dataObjectReference) {
		List<ModelElementInstance> writingNodes = bpmn.getModelElementsByType(DataOutputAssociation.class).stream()
				.filter(assoc -> assoc.getTarget().equals(dataObjectReference))
				.map(DataOutputAssociation::getParentElement)
				.collect(Collectors.toList());
		assumeFalse(writingNodes.isEmpty(), "The data object reference is not written");
		Place dataObjectPlace = dataObjectPlacesNamed(dataObjectReference.getDataObject().getName()).findAny().get();

		writingNodes.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataObjectPlace, nodeName).count(),
					"There is not exactly one write arc from node "+nodeName+" to data object reference "+dataObjectReference.getName());
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testReadDataObjectsAreWrittenBack(DataObjectReference dataObjectReference) {
		List<ModelElementInstance> readingNodes = bpmn.getModelElementsByType(DataInputAssociation.class).stream()
			.filter(assoc -> assoc.getSources().contains(dataObjectReference))
			.map(DataInputAssociation::getParentElement)
			.collect(Collectors.toList());
		assumeFalse(readingNodes.isEmpty(), "The data object reference is not read");
		Place dataObjectPlace = dataObjectPlacesNamed(dataObjectReference.getDataObject().getName()).findAny().get();
		
		readingNodes.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataObjectPlace, nodeName).count(),
					"There is not exactly one write arc from node "+nodeName+" to data object reference "+dataObjectReference.getName());
		});
	}

	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testInputAndOutputCombinations(Activity activity) {
		Map<String, List<String>> inputStates = new HashMap<>();
		Map<String, List<String>> outputStates = new HashMap<>();
		activity.getDataInputAssociations().stream()
			.flatMap(assoc -> assoc.getSources().stream())
			.filter(each -> each instanceof DataObjectReference)
			.map(DataObjectReference.class::cast)
			.filter(each -> Objects.nonNull(each.getDataState()))
			.forEach(each -> inputStates
				.computeIfAbsent(normalizeElementName(each.getDataObject().getName()), $ -> new ArrayList<>())
				.addAll(CompilerApp.dataObjectStateToNetColors(each.getDataState().getName()).collect(Collectors.toList())));
		
		activity.getDataOutputAssociations().stream()
			.map(DataOutputAssociation::getTarget)
			.filter(each -> each instanceof DataObjectReference)
			.map(DataObjectReference.class::cast)
			.filter(each -> Objects.nonNull(each.getDataState()))
			.forEach(each -> outputStates
				.computeIfAbsent(normalizeElementName(each.getDataObject().getName()), $ -> new ArrayList<>())
				.addAll(CompilerApp.dataObjectStateToNetColors(each.getDataState().getName()).collect(Collectors.toList())));
		
		assumeTrue(!inputStates.isEmpty() || !outputStates.isEmpty(), "Activity has no input or out sets");

		List<List<String>> possibleInputSets = CompilerApp.allCombinationsOf(inputStates.values());
		List<List<String>> possibleOutputSets = CompilerApp.allCombinationsOf(outputStates.values());

		if(possibleInputSets.isEmpty()) possibleInputSets.add(Collections.emptyList());
		if(possibleOutputSets.isEmpty()) possibleOutputSets.add(Collections.emptyList());

		Page activityPage = pagesNamed(normalizeElementName(activity.getName())).findAny().get();
		for(List<String> inputSet : possibleInputSets) {
			for(List<String> outputSet : possibleOutputSets) {
				assertEquals(1, activityTransitionsForTransput(activityPage, activity.getName(), inputSet, outputSet).count(), 
						"There was no arc for activity "+activity.getName()+" with inputs "+inputSet+" and outputs "+outputSet);
			}
		}
	}

}
