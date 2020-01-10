package de.uni_potsdam.hpi.bpt.fcm2cpn;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.TransitionNode;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.cpntypes.impl.CpntypesFactoryImpl;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class CompilerApp {
	
	private Page mainPage;
	private BpmnModelInstance bpmn;
	private BuildCPNUtil builder;
	private PetriNet petriNet;
	private Map<String, Page> activityPages;
	private Map<String, Node> idsToNodes;

    public static void main(final String[] args) throws Exception {
        BpmnModelInstance bpmn = loadBPMNFile("./src/main/resources/cat_example.bpmn");
        PetriNet petriNet = new CompilerApp(bpmn).translateBPMN2CPN();
        ModelPrinter.printModel(petriNet);
        DOMGenerator.export(petriNet, "./test.cpn");

    }
    
    private CompilerApp(BpmnModelInstance bpmn) {
    	this.bpmn = bpmn;
        this.builder = new BuildCPNUtil();
        this.activityPages = new HashMap<>();
        this.idsToNodes = new HashMap<>();
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
        translateEvents();
        translateGateways();
        translateControlFlow();
        return petriNet;
    }
    
    private void initializeCPNModel() {
        System.out.print("Initalizing CPN model... ");
        petriNet = builder.createPetriNet();
        mainPage = builder.addPage(petriNet, "Main Page");
        builder.declareStandardColors(petriNet);
        builder.declareColorSet(petriNet, "CaseID", CpntypesFactory.INSTANCE.createCPNString());
        System.out.println("DONE");
    }
    
    private void translateDataObjects() {
        Collection<DataObject> objectInstances = bpmn.getModelElementsByType(DataObject.class);
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
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.stream()
        	.forEach(each -> {
            	String name = each.getName();
	        	Page activityPage = builder.addPage(petriNet, normalizeActivityName(name));
	        	builder.addTransition(activityPage, name);
	            Node node = builder.createSubPageTransition(activityPage, mainPage, name);
	            activityPages.put(name, activityPage);
	            idsToNodes.put(each.getId(), node);
	        });
    }
    
    private String normalizeActivityName(String name) {
    	return name.replace('\n', ' ');
    }
    
    private void translateEvents() {
        Collection<Event> events = bpmn.getModelElementsByType(Event.class);
        events.stream().forEach(each -> {
        	Node node = builder.addTransition(mainPage, each.getName());
        	idsToNodes.put(each.getId(), node);
        });
    }
    
    private void translateGateways() {
    	//TODO only works for Xor Gateways
        Collection<Gateway> gateways = bpmn.getModelElementsByType(Gateway.class);
        gateways.stream().forEach(each -> {
        	Node node = builder.addPlace(mainPage, each.getName(), "CaseID");
        	idsToNodes.put(each.getId(), node);
        });
    }
    
    private void translateControlFlow() {
        Collection<SequenceFlow> sequenceFlows = bpmn.getModelElementsByType(SequenceFlow.class);
        sequenceFlows.stream().forEach(each -> {
        	Node source = idsToNodes.get(each.getSource().getId());
        	Node target = idsToNodes.get(each.getTarget().getId());
        	if(isPlace(source) || isPlace(target)) {
            	builder.addArc(mainPage, source, target, "");
        	} else {
            	Node place = builder.addPlace(mainPage, null, "CaseID");
            	builder.addArc(mainPage, source, place, "");
            	builder.addArc(mainPage, place, target, "");
        	}
        });
    }
    
    private static boolean isPlace(Node node) {
    	return node instanceof Place;
    }
    
    private static boolean isTransition(Node node) {
    	return node instanceof Transition;
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
