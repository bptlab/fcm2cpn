package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

import static org.junit.jupiter.api.Assertions.*;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestUtils.*;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.junit.jupiter.api.Test;

import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;


/** Test that certain warnings/errors occur, regardless of where they were thrown*/
public class ValidationTests extends ModelStructureTests {
	
	private BpmnModelInstance unchangedModel;
	private List<Exception> exceptionsDuringCompilation = new ArrayList<>();

	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testAbsenceOfIOSpecificationYieldsWarning(Activity activity) {
		Activity unchangedActivity = unchangedModel.getModelElementById(activity.getId());
		assertEquals(unchangedActivity.getIoSpecification() == null, getMessagesOfClass(NoIOSpecificationWarning.class).anyMatch(warning -> warning.activity.equals(activity)),
				"Warning for the absence of io specification was not created correctly for activity \""+elementName(activity)+"\"");
	}
	
	@Test
	@ModelsToTest("validation/UnmodeledDataObjectYieldsError")
	public void testUnmodeledDataObjectYieldsError() {
		assertExactlyOne(getMessagesOfClass(UnmodeledDataObjectError.class), "No error was thrown for unmodeled data classes in inputs");
		UnmodeledDataObjectError error = getMessagesOfClass(UnmodeledDataObjectError.class).findAny().get();
		assertEquals(normalizeElementName("Not In Model"), error.dataObject, "Thrown data model mismatch error did not list the correct unmodeled classes");
	}

	
// ============== Infrastructure ==========================
	
	private ValidationContext validationContext() {
		return compilerApp.getValidationContext();
	}
	
	private <T extends ValidationMessage> Stream<T> getMessagesOfClass(Class<T> clazz) {
		return validationContext().getMessages().stream()
			.filter(clazz::isInstance)
			.map(clazz::cast);
	}
	
	@Override
	protected void loadModel(String modelName) {
		super.loadModel(modelName);
		unchangedModel = modelNamed(modelName);
	}
	
	@Override
	public void compileModel() {
		try {
			super.compileModel();
		} catch(Exception e) {
			System.out.println("Error occurred during compilation of model "+modelName+": "+e);
			exceptionsDuringCompilation.add(e);
		}
	}

}
