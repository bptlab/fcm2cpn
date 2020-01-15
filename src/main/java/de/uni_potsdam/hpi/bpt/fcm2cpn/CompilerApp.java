package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.SourceRef;
import org.camunda.bpm.model.bpmn.impl.instance.TargetRef;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.CatchEvent;
import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Code;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.TransitionNode;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.impl.CodeImpl;
import org.cpntools.accesscpn.model.impl.PageImpl;
import org.cpntools.accesscpn.model.impl.TransitionNodeImpl;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;


public class CompilerApp {
	
	private Page mainPage;
	private BpmnModelInstance bpmn;
	private BuildCPNUtil builder;
	private PetriNet petriNet;
	private Map<String, SubpageElement> subpages;
	private Map<String, Node> idsToNodes;
	
	private Map<Arc, String> arcsToAnnotations;

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
        this.arcsToAnnotations = new HashMap<>();
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
        initializeDefaultVariables();
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
    
    private void initializeDefaultVariables() {
    	builder.declareVariable(petriNet, "count", "INT");
    	builder.declareVariable(petriNet, "caseId", "CaseID");
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
            String name = each.getName().trim().toLowerCase();
        	Node node = builder.addPlace(mainPage, name, "DATA_OBJECT");
            builder.declareVariable(petriNet, name.replaceAll("\\s", "_") + "Id", "STRING");
            builder.declareVariable(petriNet, name.replaceAll("\\s", "_") + "Count", "INT");
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
            String name = each.getName().trim().toLowerCase();
        	Node node = builder.addPlace(mainPage, name, "DATA_STORE");
            builder.declareVariable(petriNet, name.replaceAll("\\s", "_") + "Id", "STRING");
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
            Set<String> dataInputs = each.getDataInputAssociations().stream()
                    .map(assoc -> findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0)))
                    .map(source -> bpmn.getModelElementById(source))
                    .filter(object -> object instanceof DataObjectReference)
                    .map(object -> ((DataObjectReference)object).getDataObject().getName().replaceAll("\\s", "_"))
                    .collect(Collectors.toSet());
            Set<String> createObjects = each.getDataOutputAssociations().stream()
                    .map(assoc -> findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0)))
                    .map(target -> bpmn.getModelElementById(target))
                    .filter(object -> object instanceof DataObjectReference)
                    .map(object -> ((DataObjectReference)object).getDataObject().getName().replaceAll("\\s", "_"))
                    .filter(output -> !dataInputs.contains(output))
                    .collect(Collectors.toSet());
            if (createObjects.size() > 0) {
                String countVariables = createObjects.stream().map(object -> object + "Count").collect(Collectors.joining(",\n"));
                String idVariables = createObjects.stream().map(object -> object + "Id").collect(Collectors.joining(",\n"));
                String idGeneration = createObjects.stream().map(object -> "String.concat[\"" + object + "\", Int.toString(" + object + "Count)]").collect(Collectors.joining(",\n"));
                subpageTransition.getCode().setText(String.format(
                        "input (%s);\n"
                                + "output (%s);\n"
                                + "action (%s);",
                        countVariables,
                        idVariables,
                        idGeneration));
                createObjects.forEach(object -> {
                    Place caseTokenPlace = builder.addPlace(activityPage, object + " Count", "INT", "1`0");
                    builder.addArc(activityPage, caseTokenPlace, subpageTransition, object + "Count");
                    builder.addArc(activityPage, subpageTransition, caseTokenPlace, object + "Count + 1");
                });
            }
            subpages.put(each.getId(), new SubpageElement(each.getId(), activityPage, node, subpageTransition));
            idsToNodes.put(each.getId(), node);
            translateDataAssociations(node, each.getDataOutputAssociations(), each.getDataInputAssociations());
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
            
            Place caseTokenPlace = builder.addPlace(eventPage, "Case Count", "INT", "1`0");
            builder.addArc(eventPage, caseTokenPlace, subpageTransition, "count");
            builder.addArc(eventPage, subpageTransition, caseTokenPlace, "count + 1");

            String idVariables = each.getDataOutputAssociations().stream()
                    .map(assoc -> {
                        String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
                        ItemAwareElement dataObject = bpmn.getModelElementById(target);
                        return ((DataObjectReference) dataObject).getDataObject().getName().replaceAll("\\s", "_") + "Id";
                    })
                    .distinct()
                    .collect(Collectors.joining(","));
            String idGeneration = each.getDataOutputAssociations().stream()
                    .map(assoc -> {
                        String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
                        ItemAwareElement dataObject = bpmn.getModelElementById(target);
                        return ((DataObjectReference) dataObject).getDataObject().getName().replaceAll("\\s", "_");
                    })
                    .map(n -> "String.concat[\"" + n + "\", Int.toString(count)]")
                    .distinct()
                    .collect(Collectors.joining(",\n"));
            subpageTransition.getCode().setText(String.format(
            	"input (count);\n"
	            +"output (caseId,%s);\n"
    			+"action (String.concat[\"case\", Int.toString(count)],\n%s);",
                idVariables,
                idGeneration));
            
            
        	idsToNodes.put(each.getId(), node);
        	translateDataAssociations(node, each.getDataOutputAssociations(), Collections.emptyList());
        });
    }
    
    private void translateDataAssociations(Node node, Collection<DataOutputAssociation> outputs, Collection<DataInputAssociation> inputs) {
        Map<Node, Arc> outgoingArcs = new HashMap<>();
        outputs.forEach(assoc -> {
        	String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
        	ItemAwareElement dataObject = bpmn.getModelElementById(target);
        	String annotation;
        	if(dataObject instanceof DataObjectReference) {
                String dataType = ((DataObjectReference) dataObject).getDataObject().getName().replaceAll("\\s", "_");
            	String dataState = dataObject.getDataState().getName();
            	annotation = "{id = "+dataType+"Id, caseId = caseId, state = "+dataObjectStateToNetColor(dataState)+"}";
        	} else if (dataObject instanceof  DataStoreReference){
                String dataType = ((DataStoreReference) dataObject).getDataStore().getName().replaceAll("\\s", "_");
        		annotation = dataType + "Id";
        	} else {
        	    annotation = "UNKNOWN";
            }
        	outgoingArcs.computeIfAbsent(idsToNodes.get(target), targetNode -> {
    			Arc arc = builder.addArc(mainPage, node, targetNode, "");
    			arcsToAnnotations.put(arc, annotation);
    			return arc;
    		});
        });
        Map<Node, Arc> ingoingArcs = new HashMap<>();
        inputs.forEach(assoc -> {
        	String source = findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0));
            ItemAwareElement dataObject = bpmn.getModelElementById(source);
            String annotation;
            if(dataObject instanceof DataObjectReference) {
                // TODO: Add arc inscription for data objects;
                String dataType = ((DataObjectReference) dataObject).getDataObject().getName().replaceAll("\\s", "_");
                String dataState = dataObject.getDataState().getName();
                annotation = "{id = "+dataType+"Id, caseId = caseId, state = "+dataObjectStateToNetColor(dataState)+"}";
            } else if (dataObject instanceof  DataStoreReference){
                String dataType = ((DataStoreReference) dataObject).getDataStore().getName().replaceAll("\\s", "_");
                annotation = dataType + "Id";
            } else {
                annotation = "UNKNOWN";
            }
    		ingoingArcs.computeIfAbsent(idsToNodes.get(source), sourceNode -> {
    			Arc arc = builder.addArc(mainPage, sourceNode, node, "");
                arcsToAnnotations.put(arc, annotation);
    			return arc;
    		});
    		/**Assert that when reading and not writing, the unchanged token is put back*/
    		outgoingArcs.computeIfAbsent(idsToNodes.get(source), targetNode -> {
    			Arc arc = builder.addArc(mainPage, node, targetNode, "");
                arcsToAnnotations.put(arc, annotation);
    			return arc;
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
            	arcsToAnnotations.put(builder.addArc(mainPage, source, target, ""), "caseId");
        	} else {
            	Node place = builder.addPlace(mainPage, null, "CaseID");
            	arcsToAnnotations.put(builder.addArc(mainPage, source, place, ""), "caseId");
            	arcsToAnnotations.put(builder.addArc(mainPage, place, target, ""), "caseId");
        	}
        });
    }
    
    
    private void populateSubpages() {
		subpages.forEach((id, subpage) -> {
			Map<Place, RefPlace> existingRefs = new HashMap<>();
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
				existingRefs.put(sourcePlace, copyPlace);
				builder.addArc(subpage.page, copyPlace, subpage.subpageTransition, arcsToAnnotations.getOrDefault(sourceArc, "TBD"));				
			});
			
			//Outgoing
			subpage.mainTransition.getSourceArc().forEach(sourceArc -> {
				Place targetPlace_ = (Place) sourceArc.getTarget();
				RefPlace copyPlace = existingRefs.computeIfAbsent(targetPlace_, targetPlace -> builder.addReferencePlace(
					subpage.page, 
					targetPlace.getName().asString(), 
					targetPlace.getSort().getText(), 
					"", 
					targetPlace, 
					subpage.mainTransition));
				builder.addArc(subpage.page, subpage.subpageTransition, copyPlace, arcsToAnnotations.getOrDefault(sourceArc, "TBD"));				
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
