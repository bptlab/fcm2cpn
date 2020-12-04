package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;


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
		Instance eventTransition = instancesNamed(elementName(boundaryEvent)).findAny().get();
		String canceledActivityName = boundaryEvent.getAttachedTo().getName();
		Instance canceledActivityTransition = instancesNamed(canceledActivityName).findAny().get();
		
		Set<Place> activityConsumedControlFlow = canceledActivityTransition.getTargetArc().stream()
				.map(arc -> (Place)arc.getOtherEnd(canceledActivityTransition))
				.filter(this::isControlFlowPlace)
				.collect(Collectors.toSet());
		
		Set<Place> eventConsumedControlFlow = eventTransition.getTargetArc().stream()
				.map(arc -> (Place)arc.getOtherEnd(eventTransition))
				.filter(this::isControlFlowPlace)
				.collect(Collectors.toSet());
		
		
		assertEquals(activityConsumedControlFlow, eventConsumedControlFlow,
				"Cancelling boundary Event "+elementName(boundaryEvent)+" does not consume all control flow of activity: "+canceledActivityName);
		assert !activityConsumedControlFlow.isEmpty() && !eventConsumedControlFlow.isEmpty();
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
	public void testControlFlowIsMapped(SequenceFlow sequenceFlow) {
		assertEquals(1, controlFlowMappingsBetween(elementName(sequenceFlow.getSource()), elementName(sequenceFlow.getTarget())).count(), 
			"There is not exactly one mapping for the control flow between "+elementName(sequenceFlow.getSource())+" and "+elementName(sequenceFlow.getTarget()));
	}
	
	@TestWithAllModels
	@ForEachBpmn(SequenceFlow.class)
	public void testControlFlowMappingIsNamed(SequenceFlow sequenceFlow) {
		Object controlFlowMapping = controlFlowMappingsBetween(elementName(sequenceFlow.getSource()), elementName(sequenceFlow.getTarget())).findAny().get();
		assumeTrue(controlFlowMapping instanceof Node, "Only transitions and places can carry names");
		Node controlFlowMappingNode = (Node) controlFlowMapping;
		assertEquals(elementName(sequenceFlow), controlFlowMappingNode.getName().asString(),
				"Control flow mapping between "+elementName(sequenceFlow.getSource())+" and "+elementName(sequenceFlow.getTarget())+" doesn't have right name");
	}
	
	@TestWithAllModels
	@ForEachBpmn(ExclusiveGateway.class)
	public void testExclusiveGatewayPlaceIsCreated(ExclusiveGateway gateway) {
		assertEquals(1, placesNamed(elementName(gateway)).count(),
			"There is not exactly one place for exclusive gatewax "+elementName(gateway));
	}
	
	@TestWithAllModels
	@ForEachBpmn(ExclusiveGateway.class)
	public void testExclusiveGatewayPlaceHasCorrectArcs(ExclusiveGateway gateway) {
		List<SequenceFlow> unmappedFlows = Stream.concat(
				gateway.getIncoming().stream().filter(controlFlow -> controlFlowMappingsBetween(elementName(controlFlow.getSource()), elementName(gateway)).count() == 0),
				gateway.getOutgoing().stream().filter(controlFlow -> controlFlowMappingsBetween(elementName(gateway), elementName(controlFlow.getTarget())).count() == 0)
		).collect(Collectors.toList());
		assertTrue(unmappedFlows.isEmpty(), "Unmapped sequence flows for parallel gateway "+elementName(gateway)+": "
				+unmappedFlows.stream().map(each -> elementName(each.getSource())+" -> "+elementName(each.getTarget())).collect(Collectors.toList()));
	}
	
	@TestWithAllModels
	@ForEachBpmn(ParallelGateway.class)
	public void testParallelGatewayTransitionIsCreated(ParallelGateway gateway) {
		assertEquals(1, instancesNamed(elementName(gateway)).count(), 
				"There is not exactly one transition for parallel gateway "+elementName(gateway));
	}
	
	@TestWithAllModels
	@ForEachBpmn(ParallelGateway.class)
	public void testParallelGatewaySubPageIsCreated(ParallelGateway gateway) {
		assertEquals(1, pagesNamed(normalizeElementName(elementName(gateway))).count(), 
				"There is not exactly one sub page for parallel gateway "+elementName(gateway));
	}

	@TestWithAllModels
	@ForEachBpmn(ParallelGateway.class)
	public void testParallelGatewayPlaceHasCorrectArcs(ParallelGateway gateway) {		
		List<SequenceFlow> unmappedFlows = Stream.concat(
				gateway.getIncoming().stream().filter(controlFlow -> controlFlowMappingsBetween(elementName(controlFlow.getSource()), elementName(gateway)).count() == 0),
				gateway.getOutgoing().stream().filter(controlFlow -> controlFlowMappingsBetween(elementName(gateway), elementName(controlFlow.getTarget())).count() == 0)
		).collect(Collectors.toList());
		assertTrue(unmappedFlows.isEmpty(), "Unmapped sequence flows for parallel gateway "+elementName(gateway)+": "
				+unmappedFlows.stream().map(each -> elementName(each.getSource())+" -> "+elementName(each.getTarget())).collect(Collectors.toList()));
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
		List<ModelElementInstance> readingElements = readingElements(dataObjectReference);
		assumeFalse(readingElements.isEmpty(), "The data object reference is not read");
		Place dataObjectPlace = dataObjectPlacesNamed(normalizeElementName(dataObjectReference.getDataObject().getName())).findAny().get();
		
		readingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsToNodeNamed(dataObjectPlace, nodeName).count(),
					"There is not exactly one read arc from data object reference "+dataObjectReference.getName()+" to node "+nodeName);
		});	
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testWrittenDataObjectsAreProduced(DataObjectReference dataObjectReference) {
		List<ModelElementInstance> writingElements = writingElements(dataObjectReference);
		assumeFalse(writingElements.isEmpty(), "The data object reference is not written");
		Place dataObjectPlace = dataObjectPlacesNamed(normalizeElementName(dataObjectReference.getDataObject().getName())).findAny().get();

		writingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataObjectPlace, nodeName).count(),
					"There is not exactly one write arc from node "+nodeName+" to data object reference "+dataObjectReference.getName());
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testReadDataObjectsAreWrittenBack(DataObjectReference dataObjectReference) {
		List<ModelElementInstance> readingElements = readingElements(dataObjectReference);
		assumeFalse(readingElements.isEmpty(), "The data object reference is not read");
		Place dataObjectPlace = dataObjectPlacesNamed(normalizeElementName(dataObjectReference.getDataObject().getName())).findAny().get();
		
		readingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataObjectPlace, nodeName).count(),
					"There is not exactly one write back arc from reading node "+nodeName+" to data object reference "+dataObjectReference.getName());
		});
	}

	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testConsumedDataObjectsAreReproduced(Activity activity) {
		Set<Pair<Map<String, String>, Map<String, String>>> ioCombinations = ioCombinationsInNet(activity);
		ioCombinations.forEach(ioCombination -> {
			Map<String, String> inputs = ioCombination.first;
			Map<String, String> outputs = ioCombination.second;
			
			Set<String> missingWriteBacks = new HashSet<>(inputs.keySet());
			missingWriteBacks.removeAll(outputs.keySet());
			assertEquals(Collections.emptySet(), missingWriteBacks, 
					"Activity \""+elementName(activity)+"\" has a transition that consumes data elements "+missingWriteBacks+" but does not write them back;\n IO is: "+ioCombinations);
		});
	}

	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testInputAndOutputStateCombinations(Activity activity) {
		Set<Pair<Map<String, String>, Map<String, String>>> expectedCombinations = expectedIOCombinations(activity);
		Set<Pair<Map<String, String>, Map<String, String>>> supportedCombinations = ioCombinationsInNet(activity);
		
		assertEquals(supportedCombinations, expectedCombinations,
			"Possible i/o state combinations did not match for activity \""+elementName(activity)+"\":"
			+ "\n\t Expected but not present: "+expectedCombinations.stream().filter(each -> !supportedCombinations.contains(each)).collect(Collectors.toList())
			+ "\n\t Present but not Expected: "+supportedCombinations.stream().filter(each -> !expectedCombinations.contains(each)).collect(Collectors.toList())
			+ "\n"
		);
		
	}
	
	
	@TestWithAllModels
	@ForEachBpmn(DataStore.class)
	public void testDataStorePlacesAreCreated(DataStore dataStore) {
		assertEquals(1, dataStorePlacesNamed(normalizeElementName(dataStore.getName())).count(), 
			"There is not exactly one place for data store "+dataStore.getName());
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testReadDataStoresAreConsumed(DataStoreReference dataStoreReference) {
		List<ModelElementInstance> readingElements = readingElements(dataStoreReference);
		assumeFalse(readingElements.isEmpty(), "The data store reference is not read");
		Place dataStorePlace = dataStorePlacesNamed(normalizeElementName(dataStoreReference.getDataStore().getName())).findAny().get();
		
		readingElements.forEach(element -> {
			String nodeName = element.getAttributeValue("name");
			assertEquals(1, arcsToNodeNamed(dataStorePlace, nodeName).count(),
					"There is not exactly one read arc from data store reference "+dataStoreReference.getName()+" to node "+nodeName);
			
			transitionsFor((FlowElement) element).forEach(readingTransition -> {
				assertEquals(1, arcsFromNodeNamed(readingTransition, normalizeElementName(dataStoreReference.getName())).count(),
						"There is not exactly one read arc from data store reference "+dataStoreReference.getName()+" to node "+nodeName+" in subpage transition");
			});
			
		});	
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testWrittenDataStoresAreProduced(DataStoreReference dataStoreReference) {
		List<ModelElementInstance> writingElements = writingElements(dataStoreReference);
		assumeFalse(writingElements.isEmpty(), "The data store reference is not written");
		Place dataStorePlace = dataStorePlacesNamed(normalizeElementName(dataStoreReference.getDataStore().getName())).findAny().get();

		writingElements.forEach(element -> {
			String nodeName = element.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataStorePlace, nodeName).count(),
					"There is not exactly one write arc from node "+nodeName+" to data store reference "+dataStoreReference.getName());
			
			transitionsFor((FlowElement) element).forEach(writingTransition -> {
				assertEquals(1, arcsToNodeNamed(writingTransition, normalizeElementName(dataStoreReference.getName())).count(),
						"There is not exactly write arc from node "+nodeName+" to data store reference "+dataStoreReference.getName()+" in subpage transition");
			});
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testReadDataStoresAreWrittenBack(DataStoreReference dataStoreReference) {
		List<ModelElementInstance> readingElements = readingElements(dataStoreReference);
		assumeFalse(readingElements.isEmpty(), "The data Store reference is not read");
		Place dataStorePlace = dataStorePlacesNamed(normalizeElementName(dataStoreReference.getDataStore().getName())).findAny().get();
		
		readingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataStorePlace, nodeName).count(),
					"There is not exactly one write back arc from reading node "+nodeName+" to data Store reference "+dataStoreReference.getName());
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testWrittenDataStoresAreReadFirst(DataStoreReference dataStoreReference) {
		List<ModelElementInstance> writingElements = writingElements(dataStoreReference);
		assumeFalse(writingElements.isEmpty(), "The data store reference is not written");
		Place dataStorePlace = dataStorePlacesNamed(normalizeElementName(dataStoreReference.getDataStore().getName())).findAny().get();
		
		writingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsToNodeNamed(dataStorePlace, nodeName).count(),
					"There is not exactly one read arc from data store reference "+dataStoreReference.getName()+" to writing node "+nodeName);
		});
	}
	
	@TestWithAllModels
	public void testRegistryIsCreated() {
		assertEquals(1, placesNamed("objects").filter(place -> place.getSort().getText().equals("LIST_OF_ID")).count(),
				"There is not exactly one place for object registry");
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testEachCreateIsRegistered(Activity activity) {
		expectedIOCombinations(activity).forEach(ioCombination -> {
			Transition transition = transitionForIoCombination(ioCombination, activity).get();
			expectedCreatedObjects(ioCombination).forEach(createdObject -> {
				assertEquals(1, arcsToNodeNamed(transition, "objects")
						.map(arc -> arc.getHlinscription().getText())
						.filter(inscription -> {
							String[] split = inscription.split("\\^\\^");
							return split.length == 2 && split[0].equals("registry") && toSet(split[1]).contains(createdObject.first+"Id");
						})
						.count(),
					"There is not exactly one arc that registers creation "+createdObject+" in transition "+transition.getName().getText());
			});
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(StartEvent.class)
	public void testEachCreateIsRegisteredForStartEvents(StartEvent startEvent) {
		Transition transition = transitionsFor(startEvent).findAny().get();
		dataObjectToStateMap(writtenDataObjectRefs(startEvent)).entrySet().forEach(createdObject -> {
			assertEquals(1, arcsToNodeNamed(transition, "objects")
					.map(arc -> arc.getHlinscription().getText())
					.filter(inscription -> {
						String[] split = inscription.split("\\^\\^");
						return split.length == 2 && split[0].equals("registry") && toSet(split[1]).contains(createdObject.getKey()+"Id");
					})
					.count(),
				"There is not exactly one arc that registers creation "+createdObject+" in transition "+transition.getName().getText());
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testEachCreateIsCounted(Activity activity) {
		expectedIOCombinations(activity).forEach(ioCombination -> {
			Transition transition = transitionForIoCombination(ioCombination, activity).get();
			expectedCreatedObjects(ioCombination).forEach(createdObject -> {
				String objectId = createdObject.first;
				assertEquals(1, arcsFromNodeNamed(transition, objectId+"Count")
						.map(arc -> arc.getHlinscription().getText())
						.filter((objectId+"Count")::equals)
						.count(),
					"There is not exactly one arc that reads the current "+objectId+" count in transition "+transition.getName().getText());
				assertEquals(1, arcsToNodeNamed(transition, objectId+"Count")
						.map(arc -> arc.getHlinscription().getText())
						.filter((objectId+"Count"+" + 1")::equals)
						.count(),
					"There is not exactly one arc that writes the incremented "+objectId+" count in transition "+transition.getName().getText());
			});
		});
	}
	
	
	@TestWithAllModels
	@ForEachBpmn(StartEvent.class)
	public void testEachCreateIsCountedForStartEvents(StartEvent startEvent) {
		Transition transition = transitionsFor(startEvent).findAny().get();
		dataObjectToStateMap(writtenDataObjectRefs(startEvent)).entrySet().forEach(createdObject -> {
			String objectId = createdObject.getKey();
			assertEquals(1, arcsFromNodeNamed(transition, objectId+"Count")
					.map(arc -> arc.getHlinscription().getText())
					.filter((objectId+"Count")::equals)
					.count(),
				"There is not exactly one arc that reads the current "+objectId+" count in transition "+transition.getName().getText());
			assertEquals(1, arcsToNodeNamed(transition, objectId+"Count")
					.map(arc -> arc.getHlinscription().getText())
					.filter((objectId+"Count"+" + 1")::equals)
					.count(),
				"There is not exactly one arc that writes the incremented "+objectId+" count in transition "+transition.getName().getText());
		});
	}

}
