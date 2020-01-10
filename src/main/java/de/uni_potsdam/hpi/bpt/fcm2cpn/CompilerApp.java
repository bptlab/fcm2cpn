package de.uni_potsdam.hpi.bpt.fcm2cpn;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.cpntypes.impl.CpntypesFactoryImpl;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class CompilerApp {
	
	private Page mainPage;
	private BpmnModelInstance bpmn;
	private BuildCPNUtil builder;
	private PetriNet petriNet;

    public static void main(final String[] args) throws Exception {
        BpmnModelInstance bpmn = loadBPMNFile("./src/main/resources/cat_example.bpmn");
        PetriNet petriNet = new CompilerApp(bpmn).translateBPMN2CPN();
        ModelPrinter.printModel(petriNet);
        DOMGenerator.export(petriNet, "./test.cpn");

    }
    
    private CompilerApp(BpmnModelInstance bpmn) {
    	this.bpmn = bpmn;
        this.builder = new BuildCPNUtil();
	}
    
    private static BpmnModelInstance loadBPMNFile(String bpmnFileUri) {
        System.out.print("Load and parse BPMN file... ");
        File bpmnFile = new File(bpmnFileUri);
        System.out.println("DONE");
        return Bpmn.readModelFromFile(bpmnFile);
    }

    private PetriNet translateBPMN2CPN() throws Exception {
    	initializeCPNModel();
        translateDataObjects();
        translateActivites();
        ModelElementType sequenceFlowType = bpmn.getModel().getType(SequenceFlow.class);
        Collection<ModelElementInstance> sequenceFlows = bpmn.getModelElementsByType(sequenceFlowType);
        sequenceFlows.forEach(sf -> {

        });
        return petriNet;
    }
    
    private void initializeCPNModel() {
        System.out.print("Initalizing CPN model... ");
        petriNet = builder.createPetriNet();
        mainPage = builder.addPage(petriNet, "Main Page");
        builder.declareStandardColors(petriNet);
        System.out.println("DONE");
    }
    
    private void translateDataObjects() {
        ModelElementType objectType = bpmn.getModel().getType(DataObject.class);
        Collection<ModelElementInstance> objectInstances = bpmn.getModelElementsByType(objectType);
        Set<String> dataObjectNames = objectInstances.stream()
                .map(obj -> (DataObject)obj)
                .map(DataObject::getName)
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());
        dataObjectNames.forEach(s -> {
            builder.addPlace(mainPage, s, "DATA_OBJECT");
        });
        ModelElementType dataStateType = bpmn.getModel().getType(DataState.class);
        Collection<ModelElementInstance> dataStates = bpmn.getModelElementsByType(dataStateType);
        CpntypesFactory cpntypesFactory = CpntypesFactoryImpl.init();
        CPNEnum cpnEnum = cpntypesFactory.createCPNEnum();
        CPNRecord dataObject = cpntypesFactory.createCPNRecord();
        dataStates.stream().map(state -> state.getAttributeValue("name").replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s","")).collect(Collectors.toSet())
                .forEach(s -> {
                    if (!s.contains("|")) {
                        cpnEnum.addValue(s.toUpperCase());
                    }
                });
        builder.declareColorSet(petriNet, "STATE", cpnEnum);
        dataObject.addValue("id", "STRING");
        dataObject.addValue("caseId", "STRING");
        dataObject.addValue("state", "STATE");
        builder.declareColorSet(petriNet, "DATA_OBJECT", dataObject);
    }
    
    private void translateActivites() {
        ModelElementType activityType = bpmn.getModel().getType(Activity.class);
        Collection<ModelElementInstance> activities = bpmn.getModelElementsByType(activityType);
        activities.stream()
        	.map(a -> a.getAttributeValue("name"))
        	.forEach(each -> {
	        	Page activityPage = builder.addPage(petriNet, normalizeActivityName(each));
	        	builder.addTransition(activityPage, each);
	            builder.createSubPageTransition(activityPage, mainPage, each);
	        });
    }
    
    private String normalizeActivityName(String name) {
    	return name.replace('\n', ' ');
    }
    

    private static class ActivityNode {
            String name;
            String id;
            List<List<DataCondition>> preCondition;
            List<List<DataCondition>> postCondition;
    }

    private static class DataCondition {
            String dataObjectName;
            String dataObjectId;
            String dataObjectState;
            String dataObjectStateId;
    }
}
