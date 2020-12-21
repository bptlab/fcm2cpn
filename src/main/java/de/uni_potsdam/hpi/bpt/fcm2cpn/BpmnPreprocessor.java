package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.io.File;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.DataInputRefs;
import org.camunda.bpm.model.bpmn.impl.instance.DataOutputRefs;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInput;
import org.camunda.bpm.model.bpmn.instance.DataOutput;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.InputSet;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.OutputSet;

public class BpmnPreprocessor {
	
	private BpmnModelInstance bpmn;
	
	public static void main(String[] args) {
        BpmnModelInstance bpmn = Bpmn.readModelFromFile(new File("C:\\Users\\Leon Bein\\programming\\HIWI\\fcm2cpn\\src\\test\\resources\\NoIOSpecification.bpmn"));
        process(bpmn);
        System.out.println(Bpmn.convertToString(bpmn));
	}
	
	public static void process(BpmnModelInstance bpmn) {
		new BpmnPreprocessor(bpmn).process();
	}
	
	private BpmnPreprocessor(BpmnModelInstance bpmn) {
		this.bpmn = bpmn;
	}
	
	private void process() {
		ensureIOSpecifications();
		markAsPreprocessed();
	}

	private void ensureIOSpecifications() {
		
		bpmn.getModelElementsByType(Activity.class).stream()
			.filter(activity -> activity.getIoSpecification() == null)
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

	private void markAsPreprocessed() {
		bpmn.getDocumentElement().setAttributeValue("de.uni_potsdam.hpi.bpt.fcm2cpn.preprocessed", "true");
	}

}
