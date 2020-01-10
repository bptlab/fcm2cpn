package de.uni_potsdam.hpi.bpt.fcm2cpn;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import org.camunda.bpm.model.bpmn.impl.instance.DataOutputImpl;
import org.camunda.bpm.model.bpmn.impl.instance.SourceRef;
import org.camunda.bpm.model.bpmn.impl.instance.TargetRef;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
        translateData();
        translateActivites();
        translateEvents();
        translateGateways();
        translateControlFlow();
        translateDataFlow();
        return petriNet;
    }
    
    private void initializeCPNModel() {
        System.out.print("Initalizing CPN model... ");
        petriNet = builder.createPetriNet();
        mainPage = builder.addPage(petriNet, "Main Page");
        initializeDefaultColorSets();
        System.out.println("DONE");
    }
    
    private void initializeDefaultColorSets() {
        builder.declareStandardColors(petriNet);
        builder.declareColorSet(petriNet, "CaseID", CpntypesFactory.INSTANCE.createCPNString());
        builder.declareColorSet(petriNet, "DATA_STORE", CpntypesFactory.INSTANCE.createCPNString());
        
        CPNEnum cpnEnum = CpntypesFactory.INSTANCE.createCPNEnum();
        Collection<DataState> dataStates = bpmn.getModelElementsByType(DataState.class);
        dataStates.stream()
        	.map(state -> dataObjectStateToNetColor(state.getAttributeValue("name")))
            .forEach(cpnEnum::addValue);
        builder.declareColorSet(petriNet, "STATE", cpnEnum);
        
        CPNRecord dataObject = CpntypesFactory.INSTANCE.createCPNRecord();
        dataObject.addValue("id", "STRING");
        dataObject.addValue("caseId", "STRING");
        dataObject.addValue("state", "STATE");
        builder.declareColorSet(petriNet, "DATA_OBJECT", dataObject);
    }
    
    private static String dataObjectStateToNetColor(String state) {
    	return state.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s","_").toUpperCase();
    }
    
    private void translateData() {
        translateDataObjects();        
        translateDataStores();
    }
    
    
    private void translateDataObjects() {
    	Collection<DataObject> dataObjects = bpmn.getModelElementsByType(DataObject.class);
        Map<DataObject, Node> dataObjectsToPlaces = new HashMap<>();
        dataObjects.forEach(each -> {
        	Node node = builder.addPlace(mainPage, each.getName().trim().toLowerCase(), "DATA_OBJECT");
        	dataObjectsToPlaces.put(each, node);
        });
        
        Collection<DataObjectReference> dataObjectRefs = bpmn.getModelElementsByType(DataObjectReference.class);
        dataObjectRefs.forEach(each -> {
        	idsToNodes.put(each.getId(), dataObjectsToPlaces.get(each.getDataObject()));
        });
    }
    
    private void translateDataStores() {
        Collection<DataStore> dataStores = bpmn.getModelElementsByType(DataStore.class);
        Map<DataStore, Node> dataStoresToPlaces = new HashMap<>();
        dataStores.forEach(each -> {
        	Node node = builder.addPlace(mainPage, each.getName().trim().toLowerCase(), "DATA_STORE");
        	dataStoresToPlaces.put(each, node);
        });
        Collection<DataStoreReference> dataStoreRefs = bpmn.getModelElementsByType(DataStoreReference.class);
        dataStoreRefs.forEach(each -> {
        	idsToNodes.put(each.getId(), dataStoresToPlaces.get(each.getDataStore()));
        });
    }
    
    private void translateActivites() {
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.forEach(each -> {
        	String name = each.getName();
        	Page activityPage = builder.addPage(petriNet, normalizeActivityName(name));
        	builder.addTransition(activityPage, name);
            //TODO Node node = builder.createSubPageTransition(activityPage, mainPage, name);
        	Node node = builder.addTransition(mainPage, name);
            activityPages.put(name, activityPage);
            idsToNodes.put(each.getId(), node);
        });
    }
    
    private String normalizeActivityName(String name) {
    	return name.replace('\n', ' ');
    }
    
    private void translateEvents() {
        Collection<Event> events = bpmn.getModelElementsByType(Event.class);
        events.forEach(each -> {
        	Node node = builder.addTransition(mainPage, each.getName());
        	idsToNodes.put(each.getId(), node);
        });
    }
    
    private void translateGateways() {
    	//TODO only works for Xor Gateways
        Collection<Gateway> gateways = bpmn.getModelElementsByType(Gateway.class);
        gateways.forEach(each -> {
        	Node node = builder.addPlace(mainPage, each.getName(), "CaseID");
        	idsToNodes.put(each.getId(), node);
        });
    }
    
    private void translateControlFlow() {
        Collection<SequenceFlow> sequenceFlows = bpmn.getModelElementsByType(SequenceFlow.class);
        sequenceFlows.forEach(each -> {
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
    
    private static <T extends ModelElementInstance> List<String> getReferenceIds(DataAssociation association, Class<T> referenceClass) {
		return association.getChildElementsByType(referenceClass).stream()
				.map(each -> each.getTextContent())
				.collect(Collectors.toList());
    }
    
    private Node findKnownParentNode(String sourceId) {
    	ModelElementInstance element = bpmn.getModelElementById(sourceId);
    	String id = sourceId;
    	while(element != null && !idsToNodes.containsKey(id)) {
    		element = element.getParentElement();
    		id = element.getAttributeValue("id");
    	}
    	if(element != null)return idsToNodes.get(id);
    	else throw new Error("Could not find known parent element for element with id "+id);
		
    }
    
    private void translateDataFlow() {
    	Collection<DataAssociation> dataAssociations = bpmn.getModelElementsByType(DataAssociation.class);
    	dataAssociations.forEach(each -> {
    		List<String> sourceIds = getReferenceIds(each, SourceRef.class);
    		List<String> targetIds = getReferenceIds(each, TargetRef.class);
    		assert sourceIds.size() == 1 && targetIds.size() == 1;
    		Node sourceNode = findKnownParentNode(sourceIds.get(0));
    		Node targetNode = findKnownParentNode(targetIds.get(0));
    		assert Objects.nonNull(sourceNode) && Objects.nonNull(targetNode);
        	builder.addArc(mainPage, sourceNode, targetNode, "");
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
