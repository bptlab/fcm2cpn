package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.AssociationsProvider;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;


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
	public void testControlFlowPlaceIsCreated(SequenceFlow sequenceFlow) {
		assertEquals(1, controlFlowPlacesBetween(elementName(sequenceFlow.getSource()), elementName(sequenceFlow.getTarget())).count(), 
			"There is not exactly one place for the control flow between "+elementName(sequenceFlow.getSource())+" and "+elementName(sequenceFlow.getTarget()));
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
		Place gatewayPlace = placesNamed(elementName(gateway)).findAny().get();
		List<String> incomingNames = gatewayPlace.getTargetArc().stream().map(arc -> arc.getSource().getName().asString()).collect(Collectors.toList());
		List<String> outgoingNames = gatewayPlace.getSourceArc().stream().map(arc -> arc.getTarget().getName().asString()).collect(Collectors.toList());
		
		List<SequenceFlow> unmappedFlows = Stream.concat(
				gateway.getIncoming().stream().filter(controlFlow -> incomingNames.stream().noneMatch(elementName(controlFlow.getSource())::equals)),
				gateway.getOutgoing().stream().filter(controlFlow -> outgoingNames.stream().noneMatch(elementName(controlFlow.getTarget())::equals))
		).collect(Collectors.toList());
		assertTrue(unmappedFlows.isEmpty(), "Unmapped sequence flows for exclusive gateway "+elementName(gateway)+": "+unmappedFlows);
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
				gateway.getIncoming().stream().filter(controlFlow -> controlFlowPlacesBetween(elementName(controlFlow.getSource()), elementName(gateway)).count() == 0),
				gateway.getOutgoing().stream().filter(controlFlow -> controlFlowPlacesBetween(elementName(gateway), elementName(controlFlow.getTarget())).count() == 0)
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
	public void testInputAndOutputCombinations(Activity activity) {
		Set<Pair<Map<String, String>, Map<String, String>>> expectedConfigurations = new HashSet<>();
		if(activity.getIoSpecification() == null) {
			// All read states, grouped by data objects
			Map<String, List<String>> inputStates = dataObjectToStateMap(readDataObjectRefs(activity));
			Map<String, List<String>> outputStates = dataObjectToStateMap(writtenDataObjectRefs(activity));

			// All possible combinations, assuming that all possible objects are read and written
			List<Map<String, String>> possibleInputSets = indexedCombinationsOf(inputStates);
			List<Map<String, String>> possibleOutputSets = indexedCombinationsOf(outputStates);

			if(possibleInputSets.isEmpty() && !possibleOutputSets.isEmpty()) possibleInputSets.add(Collections.emptyMap());
			if(possibleOutputSets.isEmpty()&& !possibleInputSets.isEmpty()) possibleOutputSets.add(Collections.emptyMap());
			for(Map<String, String> inputSet : possibleInputSets) {
				for(Map<String, String> outputSet : possibleOutputSets) {
					expectedConfigurations.add(new Pair<>(inputSet, new HashMap<>(outputSet)));
				}
			}
		} else {
			Map<String, List<Map<String, String>>> outputSetsToPossibleForms = activity.getIoSpecification().getOutputSets().stream()
				.collect(Collectors.toMap(OutputSet::getId, outputSet -> {
					Stream<DataObjectReference> writtenReferences = outputSet.getDataOutputRefs().stream()
							.map(CompilerApp::getAssociation)
							.map(DataOutputAssociation::getTarget)
							.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
					Map<String, List<String>> outputStates = dataObjectToStateMap(writtenReferences);
					List<Map<String, String>> possibleForms = indexedCombinationsOf(outputStates);
					return possibleForms;
				}));
			activity.getIoSpecification().getInputSets().stream().forEach(inputSet -> {
				Stream<DataObjectReference> readReferences = inputSet.getDataInputs().stream()
						.map(CompilerApp::getAssociation)
						.flatMap(assoc -> assoc.getSources().stream())
						.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
				Map<String, List<String>> inputStates = dataObjectToStateMap(readReferences);
				List<Map<String, String>> possibleForms = indexedCombinationsOf(inputStates);
				
				for(OutputSet associatedOutputSet : inputSet.getOutputSets()) {
					for(Map<String, String> inputSetForm : possibleForms) {
						for(Map<String, String> outputSetForm : outputSetsToPossibleForms.get(associatedOutputSet.getId())) {
							expectedConfigurations.add(new Pair<>(inputSetForm, new HashMap<>(outputSetForm)));
						}
					}
				}
			});
		}

		//Expect read objects that are not explicitly written to be written back in the same state as they are read
		for(Pair<Map<String, String>, Map<String, String>> ioConfiguration : expectedConfigurations) {
			Map<String, String> inputSet = ioConfiguration.first;
			Map<String, String> outputSet = ioConfiguration.second;
			for(String inputObject : inputSet.keySet()) {
				if(!outputSet.containsKey(inputObject)) outputSet.put(inputObject, inputSet.get(inputObject));
			}
		}
		
		//TODO what about data stores -> ignored, as they only have one state
		//TODO what happens when there are different input states, but no output states (or vice versa) -> ignored, only one state, tested in other tests
		//TODO what happens when an object is just written back?
		
		Set<Pair<Map<String, String>, Map<String, String>>> supportedConfigurations = new HashSet<>();
		transitionsForActivity(activity).forEach(transition -> {
			Map<String, String> supportedInputs = new HashMap<>();
			Map<String, String> supportedOutputs = new HashMap<>();
			transition.getTargetArc().stream().forEach(inputArc -> {
				String dataId = null;
				String state = null;
				List<String[]> statements = Arrays.stream(inputArc.getHlinscription().asString().replaceAll("[\\{\\}]", "").split(","))
					.map(String::trim)
					.map(statement -> statement.split("="))
					.filter(statement -> statement.length == 2)
					.collect(Collectors.toList());
				for(String[] statement : statements) {
					if(statement[0].trim().equals("id")) dataId = statement[1].trim().replaceAll("Id$", "");
					if(statement[0].trim().equals("state")) state = statement[1].trim();
				}
				if(dataId != null && state != null) supportedInputs.put(dataId, state);
			});
			
			transition.getSourceArc().stream().forEach(outputArc -> {
				String dataId = null;
				String state = null;
				List<String[]> statements = Arrays.stream(outputArc.getHlinscription().asString().replaceAll("[\\{\\}]", "").split(","))
					.map(String::trim)
					.map(statement -> statement.split("="))
					.collect(Collectors.toList());
				for(String[] statement : statements) {
					if(statement.length != 2) continue;
					if(statement[0].trim().equals("id")) dataId = statement[1].trim().replaceAll("Id$", "");
					if(statement[0].trim().equals("state")) state = statement[1].trim();
				}
				if(dataId != null && state != null) supportedOutputs.put(dataId, state);
			});
			supportedConfigurations.add(new Pair<Map<String,String>, Map<String,String>>(supportedInputs, supportedOutputs));
			
		});
		
		assertEquals(expectedConfigurations, supportedConfigurations, () -> {
			StringBuilder builder = new StringBuilder("Possible i/o state configurations did not match for activity \""+normalizeElementName(activity.getName())+"\":");
			builder.append("\n\t Expected but not present: "+expectedConfigurations.stream().filter(each -> !supportedConfigurations.contains(each)).collect(Collectors.toList()));
			builder.append("\n\t Present but not Expected: "+supportedConfigurations.stream().filter(each -> !expectedConfigurations.contains(each)).collect(Collectors.toList()));
			builder.append("\n");
			return builder.toString();
		});
		
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
		
		readingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsToNodeNamed(dataStorePlace, nodeName).count(),
					"There is not exactly one read arc from data store reference "+dataStoreReference.getName()+" to node "+nodeName);
		});	
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataStoreReference.class)
	public void testWrittenDataStoresAreProduced(DataStoreReference dataStoreReference) {
		List<ModelElementInstance> writingElements = writingElements(dataStoreReference);
		assumeFalse(writingElements.isEmpty(), "The data store reference is not written");
		Place dataStorePlace = dataStorePlacesNamed(normalizeElementName(dataStoreReference.getDataStore().getName())).findAny().get();

		writingElements.forEach(node -> {
			String nodeName = node.getAttributeValue("name");
			assertEquals(1, arcsFromNodeNamed(dataStorePlace, nodeName).count(),
					"There is not exactly one write arc from node "+nodeName+" to data store reference "+dataStoreReference.getName());
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
	public void associationPlaceIsCreated() {
		assertEquals(1, placesNamed("associations").count(),
				"There is not exactly one place for associations");
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testDataAssociationsBetweenReadAndWriteAreCreated(String first, String second, Activity activity) {
		assumeTrue(reads(activity, first) && writes(activity, second), "Activity does not read the first and write the second data object.");
		assumeFalse(reads(activity, first) && reads(activity, second), "Activity reads both data objects, so an association is already in place");
		transitionsForActivity(activity).forEach(transition -> {
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName()));
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testDataAssociationsBetweenParallelWritesAreCreated(String first, String second, Activity activity) {
		assumeTrue(writes(activity, first) && writes(activity, second), "Activity does not write both data objects.");
		assumeFalse(reads(activity, first) && reads(activity, second), "Activity reads both data objects, so an association is already in place");
		transitionsForActivity(activity).forEach(transition -> {
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName()));
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testAssociationsAreCheckedWhenReading(String first, String second, Activity activity) {
		assumeTrue(reads(activity, first) && reads(activity, second), "Activity does not read both data objects.");
		transitionsForActivity(activity).forEach(transition -> {
			assertTrue(hasGuardForAssociation(transition, first+"Id", second+"Id"),
				"There is no guard for association when reading objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName()));
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testCheckedAssociationsAreNotDuplicated(String first, String second, Activity activity) {
		assumeTrue(reads(activity, first) && reads(activity, second), "Activity does not read both data objects.");
		transitionsForActivity(activity).forEach(transition -> {
			assertEquals(0, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is a writing association arc for objects "+first+" and "+second+" (which should already be associated) at activity "+normalizeElementName(activity.getName()));
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	@ForEachBpmn(DataObject.class)
	public void testNoAssociationsForUnassociatedDataObjects(DataObject first, DataObject second) {
		String dataObjectA = normalizeElementName(first.getName());
		String dataObjectB = normalizeElementName(second.getName());
		assumeFalse(dataModel.isAssociated(dataObjectA, dataObjectB));
		long numberOfAssociationArcs = petrinet.getPage().stream()
			.flatMap(page -> page.getArc().stream())
			.filter(writesAssociation(dataObjectA+"Id", dataObjectB+"Id"))
			.count();
		assertEquals(0, numberOfAssociationArcs, "There are association write arcs for data objects "+dataObjectA+" and "+dataObjectB+" though they are not associated");
	}
	

}
