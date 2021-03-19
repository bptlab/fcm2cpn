package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.dataObjectStates;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.dataPlaceName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.List;

import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.cpntools.accesscpn.model.Place;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;

public class DataElementModelStructureTests extends ModelStructureTests {
	
	
	@TestWithAllModels
	@Deprecated
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


}