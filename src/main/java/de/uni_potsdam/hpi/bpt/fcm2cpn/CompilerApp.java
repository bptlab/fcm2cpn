/**
 *     fcm2cpn is a compiler, translating process fragments to CPNtools compatible Petri nets.
 *     Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.io.File;
import java.util.*;
import java.util.function.Function;
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
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;


public class CompilerApp {

    public final static String licenseInfo = "fCM2CPN translator  Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam, Germany\n" +
            "This program comes with ABSOLUTELY NO WARRANTY.\n" +
            "This is free software, and you are welcome to redistribute it under certain conditions.\n";

	private Page mainPage;
	private BpmnModelInstance bpmn;
	private BuildCPNUtil builder;
	private PetriNet petriNet;
	private Map<String, SubpageElement> subpages;
	private Map<String, Node> idsToNodes;

    public static void main(final String[] args) throws Exception {
        System.out.println(licenseInfo);
        File bpmnFile;
        if(args.length > 0) {
        	bpmnFile = new File(args[0]);
        } else {
        	bpmnFile = getFile();
        }
        if (null == bpmnFile) {
            System.exit(0);
        }
        BpmnModelInstance bpmn = loadBPMNFile(bpmnFile);
        PetriNet petriNet = new CompilerApp(bpmn).translateBPMN2CPN();
        ModelPrinter.printModel(petriNet);
        System.out.print("Writing CPN file... ");
        DOMGenerator.export(petriNet, "./"+bpmnFile.getName().replaceAll("\\.bpmn", "")+".cpn");
        System.out.println("DONE");
    }

    private static File getFile() {
        JFileChooser chooser = new JFileChooser("./");
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "BPMN Process Model", "bpmn");
        chooser.setFileFilter(filter);
        int returnVal = chooser.showOpenDialog(null);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return  null;
    }

    private CompilerApp(BpmnModelInstance bpmn) {
    	this.bpmn = bpmn;
        this.builder = new BuildCPNUtil();
        this.subpages = new HashMap<>();
        this.idsToNodes = new HashMap<>();
	}
    
    private static BpmnModelInstance loadBPMNFile(File bpmnFile) {
        System.out.print("Load and parse BPMN file... ");
        BpmnModelInstance bpmn = Bpmn.readModelFromFile(bpmnFile);
        System.out.println("DONE");
        return bpmn;
    }

    private PetriNet translateBPMN2CPN() throws Exception {
    	initializeCPNModel();
    	System.out.print("Translating BPMN... ");
        translateData();
        translateActivities();
        translateEvents();
        translateGateways();
        translateControlFlow();
        layout();
        System.out.println("DONE");
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
        	Node node = builder.addPlace(mainPage, name, "DATA_STORE", "1`\"store_"+name.replaceAll("\\s", "_")+"\"");
            builder.declareVariable(petriNet, name.replaceAll("\\s", "_") + "Id", "STRING");
        	dataStoresToPlaces.put(each, node);
        });
        Collection<DataStoreReference> dataStoreRefs = bpmn.getModelElementsByType(DataStoreReference.class);
        dataStoreRefs.forEach(each -> {
        	idsToNodes.put(each.getId(), dataStoresToPlaces.get(each.getDataStore()));
        });
    }
    
    private void translateActivities() {
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.forEach(each -> {
        	String name = each.getName();
        	Page activityPage = builder.addPage(petriNet, normalizeActivityName(name));
            Instance mainPageTransition = builder.createSubPageTransition(activityPage, mainPage, name);
            Map<String, List<DataInputAssociation>> inputsPerObject = each.getDataInputAssociations().stream()
                    .collect(Collectors.toConcurrentMap(assoc ->  {
                        ModelElementInstance element = bpmn.getModelElementById(findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0)));
                        return dataElementId(element);
                    },
                    Arrays::asList,
                    (a,b) -> {
                        a = new ArrayList<>(a);
                        a.addAll(b);
                        return a;
                    }));
            Map<String, List<DataOutputAssociation>> outputsPerObject = each.getDataOutputAssociations().stream()
                    .collect(Collectors.toConcurrentMap(assoc ->  {
                        ModelElementInstance element = bpmn.getModelElementById(findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0)));
                        return dataElementId(element);
                    },
                    Arrays::asList,
                    (a,b) -> {
                        a = new ArrayList<>(a);
                        a.addAll(b);
                        return a;
                    }));
            int numberOfInputSets = inputsPerObject.values().stream()
                    .mapToInt(List::size)
                    .reduce(1, (a,b) -> a*b);
            List<List<DataInputAssociation>> inputSets = new ArrayList<>(numberOfInputSets);
            for(int i = 0; i < numberOfInputSets; i++) {
                int j = 1;
                List<DataInputAssociation> inputSet = new ArrayList<>();
                for(List<DataInputAssociation> objectVariants : inputsPerObject.values()) {
                    inputSet.add(objectVariants.get((i/j)%objectVariants.size()));
                    j *= objectVariants.size();
                }
                inputSets.add(inputSet);
            }
            int numberOfOutputSets = outputsPerObject.values().stream()
                    .mapToInt(List::size)
                    .reduce(1, (a,b) -> a*b);
            List<List<DataOutputAssociation>> outputSets = new ArrayList<>(numberOfInputSets);
            for(int i = 0; i < numberOfOutputSets; i++) {
                int j = 1;
                List<DataOutputAssociation> outputSet = new ArrayList<>();
                for(List<DataOutputAssociation> objectVariants : outputsPerObject.values()) {
                    outputSet.add(objectVariants.get((i/j)%objectVariants.size()));
                    j *= objectVariants.size();
                }
                outputSets.add(outputSet);
            }
            List<Transition> subpageTransitions = new ArrayList<>(numberOfInputSets * numberOfOutputSets);
            Map<DataOutputAssociation, List<Transition>> outputs = each.getDataOutputAssociations().stream()
            		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
            Map<DataInputAssociation, List<Transition>> inputs = each.getDataInputAssociations().stream()
            		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
            int inputSetIndex = 0;
            for (List<DataInputAssociation> inputSet : inputSets) {
            	int outputSetIndex = 0;
            	for (List<DataOutputAssociation> outputSet : outputSets) {
                    Transition subpageTransition = builder.addTransition(activityPage, name + inputSetIndex + "_" + outputSetIndex);
                    Set<String> dataInputs = inputSet.stream()
                            .map(assoc -> findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0)))
                            .map(source -> bpmn.getModelElementById(source))
                            .filter(object -> object instanceof DataObjectReference)
                            .map(object -> ((DataObjectReference)object).getDataObject().getName().replaceAll("\\s", "_"))
                            .collect(Collectors.toSet());
                    Set<String> createObjects = outputSet.stream()
                            .map(assoc -> findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0)))
                            .map(target -> bpmn.getModelElementById(target))
                            .filter(object -> object instanceof DataObjectReference)
                            .map(object -> ((DataObjectReference)object).getDataObject().getName().replaceAll("\\s", "_"))
                            .filter(output -> !dataInputs.contains(output))
                            .collect(Collectors.toSet());
                    if (createObjects.size() > 0) {
                    	attachObjectCreationCounters(subpageTransition, createObjects);
                    }
                    inputSet.forEach(input -> inputs.get(input).add(subpageTransition));
                    outputSet.forEach(output -> outputs.get(output).add(subpageTransition));
                    subpageTransitions.add(subpageTransition);
                    outputSetIndex++;
                }
                inputSetIndex++;
            }
            SubpageElement subPage = new SubpageElement(each.getId(), activityPage, mainPageTransition, subpageTransitions);
            subpages.putIfAbsent(each.getId(), subPage);
            idsToNodes.put(each.getId(), mainPageTransition);
            translateDataAssociations(subPage, outputs, inputs);
        });
    }
    
    private void attachObjectCreationCounters(Transition transition, Set<String> createObjects) {
        String countVariables = createObjects.stream().map(object -> object + "Count").collect(Collectors.joining(",\n"));
        String idVariables = createObjects.stream().map(object -> object + "Id").collect(Collectors.joining(",\n"));
        String idGeneration = createObjects.stream().map(object -> "String.concat[\"" + object + "\", Int.toString(" + object + "Count)]").collect(Collectors.joining(",\n"));
        Page page = transition.getPage();
        transition.getCode().setText(String.format(
                "input (%s);\n"
                        + "output (%s);\n"
                        + "action (%s);",
                countVariables,
                idVariables,
                idGeneration));
        createObjects.forEach(object -> {
            PlaceNode caseTokenPlace = builder.addFusionPlace(page, object + " Count", "INT", "1`0", object + "Count");
            builder.addArc(page, caseTokenPlace, transition, object + "Count");
            builder.addArc(page, transition, caseTokenPlace, object + "Count + 1");
        });
    }
    
    private static String normalizeActivityName(String name) {
    	return name.replace('\n', ' ');
    }
    
    private static String dataElementId(ModelElementInstance element) {
        if (element instanceof DataObjectReference) {
            return ((DataObjectReference)element).getDataObject().getName().trim().replaceAll("\\s", "");
        } else {
            return ((DataStoreReference)element).getDataStore().getName().trim().replaceAll("\\s", "");
        }
    }
    
    private void translateEvents() {
    	translateCatchEvents();
    	translateEndEvents();
    }
    
    private void translateCatchEvents() {
        Collection<CatchEvent> events = bpmn.getModelElementsByType(CatchEvent.class);
        events.forEach(each -> {
        	String name = each.getName();
        	Page eventPage = builder.addPage(petriNet, normalizeActivityName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance mainPageTransition = builder.createSubPageTransition(eventPage, mainPage, name);
            SubpageElement subPage = new SubpageElement(each.getId(), eventPage, mainPageTransition, Arrays.asList(subpageTransition));
            subpages.put(each.getId(), subPage);
            
            Place caseTokenPlace = builder.addPlace(eventPage, "Case Count", "INT", "1`0");
            builder.addArc(eventPage, caseTokenPlace, subpageTransition, "count");
            builder.addArc(eventPage, subpageTransition, caseTokenPlace, "count + 1");

            /* 
             * TODO all data outputs of event are using case count, should dedicated counters be created
             * Use Case: When the input event creates a data object that can also be created by a task
             * Then they should both use the same counter, one for the data object, and not the case counter
             */
            List<String> ids = each.getDataOutputAssociations().stream()
                    .map(assoc -> {
                        String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
                        ItemAwareElement dataObject = bpmn.getModelElementById(target);
                        return ((DataObjectReference) dataObject).getDataObject().getName().replaceAll("\\s", "_");
                    })
                    .distinct()
                    .collect(Collectors.toList());
            ids.add(0, "case");
            
            String idVariables = ids.stream().map(n -> n + "Id").collect(Collectors.joining(", "));
            String idGeneration = ids.stream().map(n -> "String.concat[\"" + n + "\", Int.toString(count)]").collect(Collectors.joining(",\n"));
            subpageTransition.getCode().setText(String.format(
            	"input (count);\n"
	            +"output (%s);\n"
    			+"action (%s);",
                idVariables,
                idGeneration));
            
            Map<DataOutputAssociation, List<Transition>> outputs = new HashMap<>();
            each.getDataOutputAssociations().forEach(assoc -> outputs.put(assoc, Arrays.asList(subpageTransition)));
        	idsToNodes.put(each.getId(), mainPageTransition);
        	translateDataAssociations(subPage, outputs, Collections.emptyMap());
        });
    }
    
    private void translateEndEvents() {
        Collection<EndEvent> events = bpmn.getModelElementsByType(EndEvent.class);
        events.forEach(each -> {
        	idsToNodes.put(each.getId(), builder.addPlace(mainPage, each.getName(), "CaseID"));
        });
    }
    
    private void translateDataAssociations(SubpageElement subPage, Map<DataOutputAssociation, List<Transition>> outputs, Map<DataInputAssociation, List<Transition>> inputs) {
    	Instance mainPageTransition = subPage.mainTransition;
    	Map<Node, Arc> outgoingArcs = new HashMap<>();
        outputs.forEach((assoc, transitions) -> {
        	String target = findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0));
        	ItemAwareElement dataObject = bpmn.getModelElementById(target);
        	String annotation = annotationForDataFlow(dataObject);
        	Node targetNode = idsToNodes.get(target);
        	outgoingArcs.computeIfAbsent(targetNode, _targetNode -> {
    			Arc arc = builder.addArc(mainPage, mainPageTransition, _targetNode, "");
    			return arc;
    		});
        	transitions.forEach(subPageTransition -> {
        		builder.addArc(subPage.page, subPageTransition, subPage.refPlaceFor((Place) targetNode), annotation);
        	});
        });
        Map<Node, Arc> ingoingArcs = new HashMap<>();
        inputs.forEach((assoc, transitions) -> {
        	String source = findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0));
            ItemAwareElement dataObject = bpmn.getModelElementById(source);
            String annotation = annotationForDataFlow(dataObject);
            Node sourceNode = idsToNodes.get(source);
    		ingoingArcs.computeIfAbsent(sourceNode, _sourceNode -> {
    			Arc arc = builder.addArc(mainPage, _sourceNode, mainPageTransition, "");
    			return arc;
    		});
        	transitions.forEach(subPageTransition -> {
        		builder.addArc(subPage.page, subPage.refPlaceFor((Place) sourceNode), subPageTransition, annotation);
        	});
    		/**Assert that when reading and not writing, the unchanged token is put back*/
    		outgoingArcs.computeIfAbsent(idsToNodes.get(source), targetNode -> {
    			Arc arc = builder.addArc(mainPage, mainPageTransition, targetNode, "");        	
            	transitions.forEach(subPageTransition -> {
            		builder.addArc(subPage.page, subPageTransition, subPage.refPlaceFor((Place) targetNode), annotation);
            	});
    			return arc;
    		});
        });
    }
    
    private String annotationForDataFlow(ItemAwareElement dataObject) {
        if(dataObject instanceof DataObjectReference) {
            String dataType = ((DataObjectReference) dataObject).getDataObject().getName().replaceAll("\\s", "_");
            String dataState = dataObject.getDataState().getName();
            return "{id = "+dataType+"Id, caseId = caseId, state = "+dataObjectStateToNetColor(dataState)+"}";
        } else if (dataObject instanceof  DataStoreReference){
            String dataType = ((DataStoreReference) dataObject).getDataStore().getName().replaceAll("\\s", "_");
            return dataType + "Id";
        } else {
            return "UNKNOWN";
        }
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
        	String sourceId = each.getSource().getId();
        	String targetId = each.getTarget().getId();
        	Node source = idsToNodes.get(sourceId);
        	Node target = idsToNodes.get(targetId);
        	//System.out.println(source.getName().asString()+" -> "+target.getName().asString());
        	if(isPlace(source) && isPlace(target)) {
        		Transition transition = builder.addTransition(mainPage, null);
        		builder.addArc(mainPage, source, transition, "caseId");
        		builder.addArc(mainPage, transition, target, "caseId");
        	} else if(isPlace(source) || isPlace(target)) {
        		builder.addArc(mainPage, source, target, "");
        		if(!isPlace(target)) {
        			SubpageElement subPage = subpages.get(targetId);
        			subPage.subpageTransitions.forEach(transition -> {
            			builder.addArc(subPage.page, subPage.refPlaceFor((Place) source), transition, "caseId");
        			});
        		}
        		if(!isPlace(source)) {
        			SubpageElement subPage = subpages.get(sourceId);
        			subPage.subpageTransitions.forEach(transition -> {
            			builder.addArc(subPage.page, transition, subPage.refPlaceFor((Place) target), "caseId");
        			});
        		}
        	} else {
            	Place place = builder.addPlace(mainPage, null, "CaseID");
            	
            	builder.addArc(mainPage, source, place, "");
       			SubpageElement sourceSubPage = subpages.get(sourceId);
    			sourceSubPage.subpageTransitions.forEach(transition -> {
        			builder.addArc(sourceSubPage.page, transition, sourceSubPage.refPlaceFor(place), "caseId");
    			});
    			
            	builder.addArc(mainPage, place, target, "");
    			SubpageElement targetSubPage = subpages.get(targetId);
    			targetSubPage.subpageTransitions.forEach(transition -> {
        			builder.addArc(targetSubPage.page, targetSubPage.refPlaceFor(place), transition, "caseId");
    			});
        	}
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
    	List<Transition> subpageTransitions;
    	Map<Place, RefPlace> placeReferences;
    	public SubpageElement(String id, Page page, Instance mainTransition, List<Transition> subpageTransitions) {
			this.id = id;
			this.page = page;
			this.mainTransition = mainTransition;
			this.subpageTransitions = subpageTransitions;
			this.placeReferences = new HashMap<>();
		}
    	
    	RefPlace refPlaceFor(Place place) {
    		return placeReferences.computeIfAbsent(place, sourcePlace -> {
    			return builder.addReferencePlace(
					page, 
					sourcePlace.getName().asString(), 
					sourcePlace.getSort().getText(), 
					"", 
					sourcePlace, 
					mainTransition);
    		});
    	}
    }
    

}
