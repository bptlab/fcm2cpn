package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.*;
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
import org.cpntools.accesscpn.model.HasName;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIdIOSet;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;


public class GeneralModelStructureTests extends ModelStructureTests {
	
	@TestWithAllModels
	public void testNetIsSound() throws Exception {
		checkNet();
	}
	
	@TestWithAllModels
	public void testFirstPageIsMainPage() {
		Page mainPage = petrinet.getPage().get(0);
		assertEquals("Main_Page", mainPage.getName().asString());
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
		assertEquals(1, instancesNamed(elementName(activity)).count(), 
				"There is not exactly one sub page transition for activity "+elementName(activity));
	}
		
	@TestWithAllModels
	@ForEachBpmn(StartEvent.class)
	public void testStartEventSubPageIsCreated(StartEvent startEvent) {
		assertEquals(1, pagesNamed(elementName(startEvent)).count(), 
				"There is not exactly one sub page for start event "+elementName(startEvent));
	}
	
	@TestWithAllModels
	@ForEachBpmn(BoundaryEvent.class)
	public void testBoundaryEventSubPageIsCreated(BoundaryEvent boundaryEvent) {
		assertEquals(1, pagesNamed(elementName(boundaryEvent)).count(), 
				"There is not exactly one sub page for boundary event "+elementName(boundaryEvent));
	}
	
	@TestWithAllModels
	@ForEachBpmn(BoundaryEvent.class)
	public void testBoundaryEventCancelsTaskExecution(BoundaryEvent boundaryEvent) {
		assumeTrue(boundaryEvent.cancelActivity());
		Instance eventTransition = instancesNamed(elementName(boundaryEvent)).findAny().get();
		String canceledActivityName = elementName(boundaryEvent.getAttachedTo());
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
		assertEquals(1, pagesNamed(elementName(activity)).count(), 
				"There is not exactly one sub page for activity "+elementName(activity));
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
		assertEquals(1, pagesNamed(elementName(gateway)).count(), 
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
		assertEquals(1, dataObjectPlacesNamed(elementName(dataObject)).count(), 
			"There is not exactly one place for data object "+elementName(dataObject));
	}
	
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testDataObjectStatePlacesAreCreated(DataObject dataObject) {
		String dataObjectName = elementName(dataObject);
		for(String state : dataObjectStates(dataObjectName, bpmn)) {
			assertEquals(1, dataObjectPlacesNamed(dataPlaceName(dataObjectName, state)).count(), 
					"There is not exactly one place for data object \""+elementName(dataObject)+"\" in state \""+state+"\"");
		}
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testReadDataObjectsAreConsumed(DataObjectReference dataObjectReference) {
		List<FlowElement> readingElements = readingElements(dataObjectReference);
		assumeFalse(readingElements.isEmpty(), "The data object reference is not read");
		Place dataObjectPlace = dataObjectPlacesNamed(elementName(dataObjectReference.getDataObject())).findAny().get();
		
		readingElements.forEach(node -> {
			String elementName = elementName(node);
			assertEquals(1, arcsToNodeNamed(dataObjectPlace, elementName).count(),
					"There is not exactly one read arc from data object reference "+elementName(dataObjectReference)+" to node "+elementName);
		});	
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testWrittenDataObjectsAreProduced(DataObjectReference dataObjectReference) {
		List<FlowElement> writingElements = writingElements(dataObjectReference);
		assumeFalse(writingElements.isEmpty(), "The data object reference is not written");
		Place dataObjectPlace = dataObjectPlacesNamed(elementName(dataObjectReference.getDataObject())).findAny().get();

		writingElements.forEach(writingElement -> {
			String elementName = elementName(writingElement);
			assertEquals(1, arcsFromNodeNamed(dataObjectPlace, elementName).count(),
					"There is not exactly one write arc from node "+elementName+" to data object reference "+elementName(dataObjectReference));
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testReadDataObjectsAreWrittenBack(DataObjectReference dataObjectReference) {
		List<FlowElement> readingElements = readingElements(dataObjectReference);
		assumeFalse(readingElements.isEmpty(), "The data object reference is not read");
		Place dataObjectPlace = dataObjectPlacesNamed(elementName(dataObjectReference.getDataObject())).findAny().get();
		
		readingElements.forEach(node -> {
			String elementName = elementName(node);
			assertEquals(1, arcsFromNodeNamed(dataObjectPlace, elementName).count(),
					"There is not exactly one write back arc from reading node "+elementName+" to data object reference "+elementName(dataObjectReference));
		});
	}

	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testConsumedDataObjectsAreReproduced(Activity activity) {
		Set<Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>> ioCombinations = ioCombinationsInNet(activity);
		ioCombinations.forEach(ioCombination -> {
			Map<Pair<String, Boolean>, String> inputs = ioCombination.first;
			Map<Pair<String, Boolean>, String> outputs = ioCombination.second;
			
			Set<Pair<String, Boolean>> missingWriteBacks = new HashSet<>(inputs.keySet());
			missingWriteBacks.removeAll(outputs.keySet());
			assertEquals(Collections.emptySet(), missingWriteBacks, 
					"Activity \""+elementName(activity)+"\" has a transition that consumes data elements "+missingWriteBacks+" but does not write them back;\n IO is: "+ioCombinations);
		});
	}

	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testInputAndOutputStateCombinations(Activity activity) {
		Set<Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>> expectedCombinations = expectedIOCombinations(activity);
		Set<Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>> supportedCombinations = ioCombinationsInNet(activity);
		
		assertEquals(supportedCombinations, expectedCombinations,
			"Possible i/o state combinations did not match for activity \""+elementName(activity)+"\":"
			+ "\n\t Expected but not present: "+expectedCombinations.stream().filter(each -> !supportedCombinations.contains(each)).collect(Collectors.toList())
			+ "\n\t Present but not Expected: "+supportedCombinations.stream().filter(each -> !expectedCombinations.contains(each)).collect(Collectors.toList())
			+ "\n"
		);
		
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testEachIOSetIsMappedToTransition(Activity activity,  DataObjectIdIOSet ioSet) {
		Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> stateMap = ioAssociationsToStateMaps(ioSet);
		assertTrue(transitionForIoCombination(stateMap, activity).isPresent(), 
				"No transition for ioSet "+stateMap+" of activity "+elementName(activity));		
	}
	
	
	@TestWithAllModels
	@ForEachBpmn(DataStore.class)
	public void testDataStorePlacesAreCreated(DataStore dataStore) {
		assertEquals(1, dataStorePlacesNamed(elementName(dataStore)).count(), 
			"There is not exactly one place for data store "+dataStore.getName());
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testReadDataStoresAreConsumed(DataStoreReference dataStoreReference) {
		List<FlowElement> readingElements = readingElements(dataStoreReference);
		assumeFalse(readingElements.isEmpty(), "The data store reference is not read");
		Place dataStorePlace = dataStorePlacesNamed(elementName(dataStoreReference.getDataStore())).findAny().get();
		
		readingElements.forEach(element -> {
			String elementName = elementName(element);
			assertEquals(1, arcsToNodeNamed(dataStorePlace, elementName).count(),
					"There is not exactly one read arc from data store reference "+elementName(dataStoreReference)+" to node "+elementName);
			
			transitionsFor((FlowElement) element).forEach(readingTransition -> {
				assertEquals(1, arcsFromNodeNamed(readingTransition, elementName(dataStoreReference)).count(),
						"There is not exactly one read arc from data store reference "+elementName(dataStoreReference)+" to node "+elementName+" in subpage transition");
			});
			
		});	
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testWrittenDataStoresAreProduced(DataStoreReference dataStoreReference) {
		List<FlowElement> writingElements = writingElements(dataStoreReference);
		assumeFalse(writingElements.isEmpty(), "The data store reference is not written");
		Place dataStorePlace = dataStorePlacesNamed(elementName(dataStoreReference.getDataStore())).findAny().get();

		writingElements.forEach(element -> {
			String elementName = elementName(element);
			assertEquals(1, arcsFromNodeNamed(dataStorePlace, elementName).count(),
					"There is not exactly one write arc from node "+elementName+" to data store reference "+elementName(dataStoreReference));
			
			transitionsFor((FlowElement) element).forEach(writingTransition -> {
				assertEquals(1, arcsToNodeNamed(writingTransition, elementName(dataStoreReference)).count(),
						"There is not exactly write arc from node "+elementName+" to data store reference "+elementName(dataStoreReference)+" in subpage transition");
			});
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testReadDataStoresAreWrittenBack(DataStoreReference dataStoreReference) {
		List<FlowElement> readingElements = readingElements(dataStoreReference);
		assumeFalse(readingElements.isEmpty(), "The data Store reference is not read");
		Place dataStorePlace = dataStorePlacesNamed(elementName(dataStoreReference.getDataStore())).findAny().get();
		
		readingElements.forEach(node -> {
			String elementName = elementName(node);
			assertEquals(1, arcsFromNodeNamed(dataStorePlace, elementName).count(),
					"There is not exactly one write back arc from reading node "+elementName+" to data Store reference "+elementName(dataStoreReference));
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testWrittenDataStoresAreReadFirst(DataStoreReference dataStoreReference) {
		List<FlowElement> writingElements = writingElements(dataStoreReference);
		assumeFalse(writingElements.isEmpty(), "The data store reference is not written");
		Place dataStorePlace = dataStorePlacesNamed(elementName(dataStoreReference.getDataStore())).findAny().get();
		
		writingElements.forEach(node -> {
			String elementName = elementName(node);
			assertEquals(1, arcsToNodeNamed(dataStorePlace, elementName).count(),
					"There is not exactly one read arc from data store reference "+elementName(dataStoreReference)+" to writing node "+elementName);
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
							return split.length == 2 && split[0].equals("registry") && toSet(split[1]).contains(createdObject.first.first+"Id");
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
				String objectId = createdObject.first.first;
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
	
	/**@see BpmnPreprocessorTests#testModelsAreMarkedAsPreprocessed()*/
	@TestWithAllModels
	public void testModelsArePreprocessed() {
		assertEquals("true", bpmn.getDocumentElement().getAttributeValue("de.uni_potsdam.hpi.bpt.fcm2cpn.preprocessed"),
				"Model "+modelName+" was not marked as preprocessed");
	}
	
	@TestWithAllModels
	public void testAllCpnIdentifiersHaveNoWhitespace() {
		Stream<HasName> allNodesInNet = Stream.concat(
			petrinet.getPage().stream(),
			petrinet.getPage().stream().flatMap(this::allNodes)
		);
		
		allNodesInNet.forEach(node -> {
			String name = node.getName().asString();
			assertEquals(name.replaceAll("\\s",""), name, 
					"Name \""+name+"\" of node "+node+" contains whitespaces.");
		});
	}

}
