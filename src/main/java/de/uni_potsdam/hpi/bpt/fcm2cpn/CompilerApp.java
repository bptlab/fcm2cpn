package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.SourceRef;
import org.camunda.bpm.model.bpmn.impl.instance.TargetRef;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.CatchEvent;
import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.impl.PageImpl;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;


public class CompilerApp {
	
	private Page mainPage;
	private BpmnModelInstance bpmn;
	private BuildCPNUtil builder;
	private PetriNet petriNet;
	private Map<String, SubpageElement> subpages;
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
        this.subpages = new HashMap<>();
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
        populateSubpages();
        //translateDataFlow();
        layout();
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
        	Transition subpageTransition = builder.addTransition(activityPage, name);
        	Instance node = builder.createSubPageTransition(activityPage, mainPage, name);
            subpages.put(each.getId(), new SubpageElement(each.getId(), activityPage, node, subpageTransition));
            idsToNodes.put(each.getId(), node);

            Map<Node, Arc> outgoingArcs = new HashMap<>();
            each.getDataOutputAssociations().forEach(assoc -> {
            	String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
        		outgoingArcs.computeIfAbsent(idsToNodes.get(target), targetNode -> {
        			return builder.addArc(mainPage, node, targetNode, "");
        		});//TODO add annotations to arc
            });
            Map<Node, Arc> ingoingArcs = new HashMap<>();
            each.getDataInputAssociations().forEach(assoc -> {
            	String source = findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0));
        		ingoingArcs.computeIfAbsent(idsToNodes.get(source), sourceNode -> {
        			return builder.addArc(mainPage, sourceNode, node, "");
        		});//TODO add annotations to arc
        		
        		/**Assert that when reading and not writing, the unchanged token is put back*/
        		outgoingArcs.computeIfAbsent(idsToNodes.get(source), targetNode -> {
        			return builder.addArc(mainPage, node, targetNode, "");
        		});//TODO add annotations to arc
            });
        });
    }
    
    private String normalizeActivityName(String name) {
    	return name.replace('\n', ' ');
    }
    
    private void translateEvents() {
        Collection<CatchEvent> events = bpmn.getModelElementsByType(CatchEvent.class);
        events.forEach(each -> {
        	String name = each.getName();
        	Page eventPage = builder.addPage(petriNet, normalizeActivityName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance node = builder.createSubPageTransition(eventPage, mainPage, name);
            subpages.put(each.getId(), new SubpageElement(each.getId(), eventPage, node, subpageTransition));
            
        	idsToNodes.put(each.getId(), node);
            Map<Node, Arc> outgoingArcs = new HashMap<>();
            each.getDataOutputAssociations().forEach(assoc -> {
            	String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
        		outgoingArcs.computeIfAbsent(idsToNodes.get(target), targetNode -> {
        			return builder.addArc(mainPage, node, targetNode, "");
        		});//TODO add annotations to arc
            });
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
    
    
    private void populateSubpages() {
		subpages.forEach((id, subpage) -> {
			//Incoming
			subpage.mainTransition.getTargetArc().forEach(sourceArc -> {
				Place sourcePlace = (Place) sourceArc.getSource();
				RefPlace copyPlace = builder.addReferencePlace(
					subpage.page, 
					sourcePlace.getName().asString(), 
					sourcePlace.getSort().getText(), 
					"", 
					sourcePlace, 
					subpage.mainTransition);
				//TODO fusion the places; there also seems to be an error when arcs go in both directions
				builder.addArc(subpage.page, copyPlace, subpage.subpageTransition, "");				
			});
			
			//Outgoing
			subpage.mainTransition.getSourceArc().forEach(sourceArc -> {
				Place targetPlace = (Place) sourceArc.getTarget();
				RefPlace copyPlace = builder.addReferencePlace(
					subpage.page, 
					targetPlace.getName().asString()+"_", 
					targetPlace.getSort().getText(), 
					"", 
					targetPlace, 
					subpage.mainTransition);
				builder.addArc(subpage.page, subpage.subpageTransition, copyPlace, "");				
			});
		});
	}
    
    private static <T extends ModelElementInstance> List<String> getReferenceIds(DataAssociation association, Class<T> referenceClass) {
		return association.getChildElementsByType(referenceClass).stream()
				.map(each -> each.getTextContent())
				.collect(Collectors.toList());
    }
    
    private String findKnownParent(String sourceId) {
    	ModelElementInstance element = bpmn.getModelElementById(sourceId);
    	String id = sourceId;
    	while(element != null && !idsToNodes.containsKey(id)) {
    		element = element.getParentElement();
    		id = element.getAttributeValue("id");
    	}
    	if(element != null)return id;
    	else throw new Error("Could not find known parent element for element with id "+id);
		
    }
    
    private static boolean isPlace(Node node) {
    	return node instanceof Place;
    }
    
    private void layout() {
    }
    
    private class SubpageElement {
    	String id;
    	Page page;
    	Instance mainTransition;
    	Transition subpageTransition;
    	public SubpageElement(String id, Page page, Instance mainTransition, Transition subpageTransition) {
			this.id = id;
			this.page = page;
			this.mainTransition = mainTransition;
			this.subpageTransition = subpageTransition;
		}
    }
    

}
