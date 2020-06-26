package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
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

@ModelsToTest({
	"Simple", 
	"SimpleWithStates", 
	"SimpleWithEvents", 
	"SimpleWithGateways", 
	"SimpleWithDataStore", 
	"TranslationJob",
	"Associations"
})
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
		transitionsForActivity(activity).forEach(transition -> {
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(isListInscriptionFor(first+"Id", second+"Id")).count(),
				"There is not exactly one association arc for objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName()));
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testDataAssociationsBetweenParallelWritesAreCreated(String first, String second, Activity activity) {
		assumeTrue(writes(activity, first) && writes(activity, second), "Activity does not write both data objects.");
		transitionsForActivity(activity).forEach(transition -> {
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(isListInscriptionFor(first+"Id", second+"Id")).count(),
				"There is not exactly one association arc for objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName()));
		});
	}
	

}
