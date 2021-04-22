package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.Objects;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.DataInputRefs;
import org.camunda.bpm.model.bpmn.impl.instance.DataOutputRefs;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInput;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutput;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.InputSet;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.OutputSet;

import de.uni_potsdam.hpi.bpt.fcm2cpn.validation.NoIOSpecificationWarning;
import de.uni_potsdam.hpi.bpt.fcm2cpn.validation.ValidationContext;

public class BpmnPreprocessor {
	
	public static final String BLANK_STATE = "BLANK_";
	
	private final BpmnModelInstance bpmn;
	private final ValidationContext validationContext;
	
	public static void process(BpmnModelInstance bpmn, ValidationContext validationContext) {
		new BpmnPreprocessor(bpmn, validationContext).process();
	}
	
	private BpmnPreprocessor(BpmnModelInstance bpmn, ValidationContext validationContext) {
		this.bpmn = bpmn;
		this.validationContext = validationContext;
	}
	
	private void process() {
		ensureIOSpecifications();
		ensureDataObjectStates();
		markAsPreprocessed();
	}

	private void ensureIOSpecifications() {
		
		bpmn.getModelElementsByType(Activity.class).stream()
			.filter(activity -> activity.getIoSpecification() == null)
			.peek(activity -> validationContext.warn(new NoIOSpecificationWarning(activity)))
			.forEach(activity -> {
				IoSpecification ioSpecification = bpmn.newInstance(IoSpecification.class, "ioSpecification_generated_"+activity.getId());
				activity.addChildElement(ioSpecification);
				
				InputSet inputSet = bpmn.newInstance(InputSet.class, "inputSet_generated_"+activity.getId());
				ioSpecification.addChildElement(inputSet);
				
				OutputSet outputSet = bpmn.newInstance(OutputSet.class, "outputSet_generated_"+activity.getId());
				ioSpecification.addChildElement(outputSet);
				
				inputSet.getOutputSets().add(outputSet);
				outputSet.getInputSetRefs().add(inputSet);
				
				activity.getDataInputAssociations().stream().forEach(inputAssoc -> {
					if(inputAssoc.getSources().stream().noneMatch(source -> source instanceof DataStoreReference)) {
						String id = "dataInput_generated_"+inputAssoc.getId();
						DataInput input = bpmn.newInstance(DataInput.class, id);
						ioSpecification.getDataInputs().add(input);
						
						DataInputRefs inputRef = bpmn.newInstance(DataInputRefs.class);
						inputRef.setTextContent(id);
						inputSet.addChildElement(inputRef);
						inputAssoc.setTarget(input);
					}
				});
				
				activity.getDataOutputAssociations().stream().forEach(outputAssoc -> {
					if(!(outputAssoc.getTarget() instanceof DataStoreReference)) {
						String id = "dataOutput_generated_"+outputAssoc.getId();
						DataOutput output = bpmn.newInstance(DataOutput.class, id);
						ioSpecification.getDataOutputs().add(output);
						
						DataOutputRefs outputRef = bpmn.newInstance(DataOutputRefs.class);
						outputRef.setTextContent(id);
						outputSet.addChildElement(outputRef);					
						outputAssoc.getSources().add(output);
					}
				});

			});
	}
	
	private void ensureDataObjectStates() {
		bpmn.getModelElementsByType(DataObjectReference.class).stream()
			.filter(dataObjectReference -> Objects.isNull(dataObjectReference.getDataState()))
			.forEach(dataObjectReference -> {
				DataState newState = bpmn.newInstance(DataState.class, "blankState_generated_"+dataObjectReference.getId());
				newState.setName(BLANK_STATE);
				dataObjectReference.setDataState(newState);
			});
	}

	private void markAsPreprocessed() {
		bpmn.getDocumentElement().setAttributeValue("de.uni_potsdam.hpi.bpt.fcm2cpn.preprocessed", "true");
	}

}
