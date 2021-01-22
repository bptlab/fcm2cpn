package de.uni_potsdam.hpi.bpt.fcm2cpn;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.junit.jupiter.api.Assertions;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelConsumerTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class BpmnPreprocessorTests extends ModelConsumerTest {
	
	@Override
	public void compileModel() {
		BpmnPreprocessor.process(bpmn);
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testIOSpecificationsAreEnsuredForAllActivities(Activity activity) {
		Assertions.assertNotNull(activity.getIoSpecification(), "Activity "+Utils.elementName(activity)+" has no io specification after preprocessing");
	}
	
	@TestWithAllModels
	public void testModelsAreMarkedAsPreprocessed() {
		Assertions.assertEquals("true", bpmn.getDocumentElement().getAttributeValue("de.uni_potsdam.hpi.bpt.fcm2cpn.preprocessed"),
				"Model "+modelName+" was not marked as preprocessed");
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObjectReference.class)
	public void testDataObjectStatesAreAddedAsNeeded(DataObjectReference dataObjectReference) {
		Assertions.assertNotNull(dataObjectReference.getDataState(), 
				"No blank data state was added to data object reference "+Utils.elementName((ItemAwareElement) dataObjectReference));
	}
	

}
