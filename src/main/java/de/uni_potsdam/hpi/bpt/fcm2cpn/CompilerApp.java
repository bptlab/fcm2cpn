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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.SourceRef;
import org.camunda.bpm.model.bpmn.impl.instance.TargetRef;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNList;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;


public class CompilerApp {

    public final static String licenseInfo = "fCM2CPN translator  Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam, Germany\n" +
            "This program comes with ABSOLUTELY NO WARRANTY.\n" +
            "This is free software, and you are welcome to redistribute it under certain conditions.\n";

	private Page mainPage;
	private BpmnModelInstance bpmn;
	private DataModel dataModel = new DataModel();
	private BuildCPNUtil builder;
	private PetriNet petriNet;
	private Map<String, SubpageElement> subpages;
	private Map<String, Node> idsToNodes;
	private Map<String, Map<Page, PlaceNode>> creationCounterPlaces;
	private Place associations;
	
	private List<Runnable> deferred;

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
        this.creationCounterPlaces = new HashMap<>();
        this.deferred = new ArrayList<>();
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
        runDeferredCalls();
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
        	.flatMap(state -> dataObjectStateToNetColors(state.getAttributeValue("name")))
            .forEach(cpnEnum::addValue);
        if(!dataStates.isEmpty())builder.declareColorSet(petriNet, "STATE", cpnEnum);
        
        CPNRecord dataObject = CpntypesFactory.INSTANCE.createCPNRecord();
        dataObject.addValue("id", "STRING");
        dataObject.addValue("caseId", "STRING");
        if(!dataStates.isEmpty())dataObject.addValue("state", "STATE");
        builder.declareColorSet(petriNet, "DATA_OBJECT", dataObject);
        
        CPNList asssociation = CpntypesFactory.INSTANCE.createCPNList();
        asssociation.setSort("STRING");
        builder.declareColorSet(petriNet, "ASSOCIATION", asssociation);
    }
    
    private void initializeDefaultVariables() {
    	builder.declareVariable(petriNet, "count", "INT");
    	builder.declareVariable(petriNet, "caseId", "CaseID");
    	builder.declareVariable(petriNet, "assoc", "ASSOCIATION");
    }
    
    private static Stream<String> dataObjectStateToNetColors(String state) {
    	return Arrays.stream(state.replaceAll("\\[", "").replaceAll("\\]", "").split("\\|"))
    			.map(String::trim)
    			.map(each -> each.replaceAll("\\s","_"))
    			.map(String::toUpperCase);
    }
    
    /**
     * Maps from top level data object names to names of
     */
    private static String trimDataObjectName(String name) {
    	return name.trim().toLowerCase();
    }
    
    private static String dataType(ItemAwareElement dataObject) {
        if(dataObject instanceof DataObjectReference) {
            return trimDataObjectName(((DataObjectReference) dataObject).getDataObject().getName());
        } else if (dataObject instanceof  DataStoreReference){
            return trimDataObjectName(((DataStoreReference) dataObject).getDataStore().getName());
        } else {
            throw new RuntimeException("Unsupported type of ItemAwareElement with id"+dataObject.getId()+": "+dataObject.getClass());
        }
    }
    
    private static String dataObjectId(String trimmedName) {
    	return trimmedName.replaceAll("\\s", "_") + "Id";
    }
    
    
    private static String dataObjectCount(String trimmedName) {
    	return trimmedName.replaceAll("\\s", "_") + "Count";
    }
    
    private void translateData() {
        translateDataObjects();        
        translateDataStores();
        createAssociationPlace();
    }
    
    
    private void translateDataObjects() {
    	Collection<DataObject> dataObjects = bpmn.getModelElementsByType(DataObject.class);
        Map<String, Node> dataObjectsNamesToPlaces = new HashMap<>();
        dataObjects.forEach(each -> {
            String name = trimDataObjectName(each.getName());
            if(!dataObjectsNamesToPlaces.containsKey(name)) {
            	Node node = builder.addPlace(mainPage, name, "DATA_OBJECT");
                builder.declareVariable(petriNet, dataObjectId(name), "STRING");
                builder.declareVariable(petriNet, dataObjectCount(name), "INT");
                dataObjectsNamesToPlaces.put(name, node);
            }
        });
        
        Collection<DataObjectReference> dataObjectRefs = bpmn.getModelElementsByType(DataObjectReference.class);
        dataObjectRefs.forEach(each -> {
        	idsToNodes.put(each.getId(), dataObjectsNamesToPlaces.get(trimDataObjectName(each.getDataObject().getName())));
        });
    }
    
    private void translateDataStores() {
        Collection<DataStore> dataStores = bpmn.getModelElementsByType(DataStore.class);
        Map<DataStore, Node> dataStoresToPlaces = new HashMap<>();
        dataStores.forEach(each -> {
            String name = trimDataObjectName(each.getName());
        	Node node = builder.addPlace(mainPage, name, "DATA_STORE", "1`\"store_"+name.replaceAll("\\s", "_")+"\"");
            builder.declareVariable(petriNet, dataObjectId(name), "STRING");
        	dataStoresToPlaces.put(each, node);
        });
        Collection<DataStoreReference> dataStoreRefs = bpmn.getModelElementsByType(DataStoreReference.class);
        dataStoreRefs.forEach(each -> {
        	idsToNodes.put(each.getId(), dataStoresToPlaces.get(each.getDataStore()));
        });
    }
    
    private void createAssociationPlace() {
    	associations = builder.addPlace(mainPage, "associations", "ASSOCIATION");
    }
    
    private void translateActivities() {
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.forEach(each -> {
        	String name = each.getName();
        	Page activityPage = builder.addPage(petriNet, normalizeActivityName(name));
            Instance mainPageTransition = builder.createSubPageTransition(activityPage, mainPage, name);
            SubpageElement subPage = new SubpageElement(this, each.getId(), activityPage, mainPageTransition, new ArrayList<>());
            List<Transition> subpageTransitions = subPage.getSubpageTransitions();
            subpages.putIfAbsent(each.getId(), subPage);
            idsToNodes.put(each.getId(), mainPageTransition);
            Map<String, List<StatefulDataAssociation<DataInputAssociation>>> inputsPerObject = each.getDataInputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
                    .collect(Collectors.toConcurrentMap(
                    		assoc ->  dataElementId(assoc.dataElement),
                    		Arrays::asList,
		                    (a,b) -> {
		                        a = new ArrayList<>(a);
		                        a.addAll(b);
		                        return a;
		                    }
                    ));
            Map<String, List<StatefulDataAssociation<DataOutputAssociation>>> outputsPerObject = each.getDataOutputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
                    .collect(Collectors.toConcurrentMap(
                    		assoc ->  dataElementId(assoc.dataElement),
                    		Arrays::asList,
		                    (a,b) -> {
		                        a = new ArrayList<>(a);
		                        a.addAll(b);
		                        return a;
		                    }
                    ));
            int numberOfInputSets = inputsPerObject.values().stream()
                    .mapToInt(List::size)
                    .reduce(1, (a,b) -> a*b);
            List<List<StatefulDataAssociation<DataInputAssociation>>> inputSets = new ArrayList<>(numberOfInputSets);
            for(int i = 0; i < numberOfInputSets; i++) {
                int j = 1;
                List<StatefulDataAssociation<DataInputAssociation>> inputSet = new ArrayList<>();
                for(List<StatefulDataAssociation<DataInputAssociation>> objectVariants : inputsPerObject.values()) {
                    inputSet.add(objectVariants.get((i/j)%objectVariants.size()));
                    j *= objectVariants.size();
                }
                inputSets.add(inputSet);
            }
            int numberOfOutputSets = outputsPerObject.values().stream()
                    .mapToInt(List::size)
                    .reduce(1, (a,b) -> a*b);
            List<List<StatefulDataAssociation<DataOutputAssociation>>> outputSets = new ArrayList<>(numberOfInputSets);
            for(int i = 0; i < numberOfOutputSets; i++) {
                int j = 1;
                List<StatefulDataAssociation<DataOutputAssociation>> outputSet = new ArrayList<>();
                for(List<StatefulDataAssociation<DataOutputAssociation>> objectVariants : outputsPerObject.values()) {
                    outputSet.add(objectVariants.get((i/j)%objectVariants.size()));
                    j *= objectVariants.size();
                }
                outputSets.add(outputSet);
            }

            Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputs = each.getDataOutputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
            Map<StatefulDataAssociation<DataInputAssociation>, List<Transition>> inputs = each.getDataInputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
            int inputSetIndex = 0;
            for (List<StatefulDataAssociation<DataInputAssociation>> inputSet : inputSets) {
            	int outputSetIndex = 0;
            	for (List<StatefulDataAssociation<DataOutputAssociation>> outputSet : outputSets) {
                    Transition subpageTransition = builder.addTransition(activityPage, name + inputSetIndex + "_" + outputSetIndex);
                    Set<String> dataInputs = inputSet.stream()
                            .map(assoc -> assoc.dataElement)
                            .filter(object -> object instanceof DataObjectReference)
                            .map(object -> trimDataObjectName(((DataObjectReference)object).getDataObject().getName()))
                            .collect(Collectors.toSet());
                    Set<String> createObjects = outputSet.stream()
                            .map(assoc -> assoc.dataElement)
                            .filter(object -> object instanceof DataObjectReference)
                            .map(object -> trimDataObjectName(((DataObjectReference)object).getDataObject().getName()))
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
            translateDataAssociations(subPage, outputs, inputs);
        });
    }
    
    private void attachObjectCreationCounters(Transition transition, Set<String> createObjects) {
        String countVariables = createObjects.stream().map(CompilerApp::dataObjectCount).collect(Collectors.joining(",\n"));
        String idVariables = createObjects.stream().map(CompilerApp::dataObjectId).collect(Collectors.joining(",\n"));
        String idGeneration = createObjects.stream().map(object -> "String.concat[\"" + object + "\", Int.toString(" + dataObjectCount(object) +")]").collect(Collectors.joining(",\n"));
        Page page = transition.getPage();
        transition.getCode().setText(String.format(
                "input (%s);\n"
                        + "output (%s);\n"
                        + "action (%s);",
                countVariables,
                idVariables,
                idGeneration));
        createObjects.forEach(object -> {
            PlaceNode caseTokenPlace = counterFor(page, object);
            builder.addArc(page, caseTokenPlace, transition, dataObjectCount(object));
            builder.addArc(page, transition, caseTokenPlace, dataObjectCount(object) + "+ 1");
        });
    }
    
    private PlaceNode counterFor(Page page, String dataObjectName) {
    	return creationCounterPlaces
    		.computeIfAbsent(dataObjectName, _dataObjectName -> new HashMap<Page, PlaceNode>())
    		.computeIfAbsent(page, _page -> builder.addFusionPlace(page, dataObjectName + " Count", "INT", "1`0", dataObjectCount(dataObjectName)));
    }
    
    private void translateEvents() {
    	translateStartEvents();
    	translateEndEvents();
    	translateBoundaryEvents();
    }

	private void translateStartEvents() {
        Collection<StartEvent> events = bpmn.getModelElementsByType(StartEvent.class);
        events.forEach(each -> {
        	String name = each.getName();
        	Page eventPage = builder.addPage(petriNet, normalizeActivityName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance mainPageTransition = builder.createSubPageTransition(eventPage, mainPage, name);
        	idsToNodes.put(each.getId(), mainPageTransition);
            SubpageElement subPage = new SubpageElement(this, each.getId(), eventPage, mainPageTransition, Arrays.asList(subpageTransition));
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
                        return trimDataObjectName(((DataObjectReference) dataObject).getDataObject().getName());
                    })
                    .distinct()
                    .collect(Collectors.toList());
            ids.add(0, "case");
            
            String idVariables = ids.stream().map(CompilerApp::dataObjectId).collect(Collectors.joining(", "));
            String idGeneration = ids.stream().map(n -> "String.concat[\"" + n + "\", Int.toString(count)]").collect(Collectors.joining(",\n"));
            subpageTransition.getCode().setText(String.format(
            	"input (count);\n"
	            +"output (%s);\n"
    			+"action (%s);",
                idVariables,
                idGeneration));
            
            Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputs = new HashMap<>();
            each.getDataOutputAssociations().stream()
            	.flatMap(this::splitDataAssociationByState)
            	.forEach(assoc -> outputs.put(assoc, Arrays.asList(subpageTransition)));
        	translateDataAssociations(subPage, outputs, Collections.emptyMap());
        });
    }
    
    private void translateEndEvents() {
        Collection<EndEvent> events = bpmn.getModelElementsByType(EndEvent.class);
        events.forEach(each -> {
        	idsToNodes.put(each.getId(), builder.addPlace(mainPage, each.getName(), "CaseID"));
        });
    }

    
    private void translateBoundaryEvents() {
    	//TODO only interrupting is supported
        Collection<BoundaryEvent> events = bpmn.getModelElementsByType(BoundaryEvent.class);
        events.forEach(each -> {
        	String name = each.getName();
        	Page eventPage = builder.addPage(petriNet, normalizeActivityName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance mainPageTransition = builder.createSubPageTransition(eventPage, mainPage, name);
            SubpageElement subPage = new SubpageElement(this, each.getId(), eventPage, mainPageTransition, Arrays.asList(subpageTransition));
            subpages.put(each.getId(), subPage);
        	idsToNodes.put(each.getId(), mainPageTransition);
            
        	String attachedId = each.getAttachedTo().getId();
        	Node attachedNode = idsToNodes.get(attachedId);
        	assert !isPlace(attachedNode);
        	defer(() -> {
            	attachedNode.getTargetArc().stream()
	    			.map(arc -> arc.getPlaceNode())
	    			.filter(place -> place.getSort().getText().equals("CaseID"))
	    			.forEach(place -> {
	    				builder.addArc(eventPage, subPage.refPlaceFor((Place) place), subpageTransition, "caseId");
	    				builder.addArc(mainPage, place, mainPageTransition, "");
	    			});
        	});
        });
	}
    
    private <T extends DataAssociation> Stream<StatefulDataAssociation<T>> splitDataAssociationByState(T assoc) {
    	ModelElementInstance source = bpmn.getModelElementById(findKnownParent(getReferenceIds(assoc, SourceRef.class).get(0)));
    	ModelElementInstance target = bpmn.getModelElementById(findKnownParent(getReferenceIds(assoc, TargetRef.class).get(0)));
        ItemAwareElement dataElement = (ItemAwareElement) (source instanceof DataObjectReference || source instanceof DataStoreReference ? source : target);
    	Stream<String> possibleStates = Optional.ofNullable(dataElement.getDataState())
        		.map(DataState::getName)
        		.map(stateName -> dataObjectStateToNetColors(stateName))
        		.orElse(Stream.of((String)null));
        return possibleStates.map(state -> new StatefulDataAssociation<>(assoc, state, dataElement));
    }
    
    private void translateDataAssociations(SubpageElement subPage, Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputs, Map<StatefulDataAssociation<DataInputAssociation>, List<Transition>> inputs) {
    	System.out.println("\n"+subPage.id);
    	
    	List<String> outputObjects = outputs.keySet().stream()
    		.peek(each -> System.out.println("\t"+each.dataElement.getClass().getSimpleName()))
    		.map(each -> each.dataElement.getId())
    		.distinct()
    		.collect(Collectors.toList());
    	
    	List<String> inputObjects = outputs.keySet().stream()
    		.peek(each -> System.out.println("\t"+each.dataElement.getClass().getSimpleName()))
    		.map(each -> each.dataElement.getId())
    		.distinct()
    		.collect(Collectors.toList());
    	
    	List<List<String>> createdAssocs = new ArrayList<>();
    	for(int i = 0; i < outputObjects.size(); i++) {
    		String output = outputObjects.get(i);
    		for(int j = i+1; j < outputObjects.size(); j++) {
    			String otherOutput = outputObjects.get(j);
    			if(dataModel.isAssociated(output, otherOutput)) createdAssocs.add(Arrays.asList(output, otherOutput));
    		}
    		
    		for(int j = 0; j < inputObjects.size(); j++) {
    			String input = inputObjects.get(j);
    			if(dataModel.isAssociated(output, input)) createdAssocs.add(Arrays.asList(output, input));
    		}
    	}
    	
    	List<List<String>> requiredAssocs = new ArrayList<>();
    	for(int i = 0; i < inputObjects.size(); i++) {
    		String input = inputObjects.get(i);
    		for(int j = i+1; j < inputObjects.size(); j++) {
    			String otherInput = inputObjects.get(j);
    			if(dataModel.isAssociated(input, otherInput)) requiredAssocs.add(Arrays.asList(input, otherInput));
    		}
    	}
    	
    	System.out.println("\t Created:"+createdAssocs);
    	System.out.println("\t Required:"+requiredAssocs);
    	
    	Instance mainPageTransition = subPage.getMainTransition();
    	Map<Node, Arc> outgoingArcs = new HashMap<>();
        outputs.forEach((assoc, transitions) -> {
        	ItemAwareElement dataElement = assoc.dataElement;
        	String annotation = annotationForDataFlow(dataElement, assoc.stateName);
        	Node targetNode = idsToNodes.get(dataElement.getId());
        	outgoingArcs.computeIfAbsent(targetNode, _targetNode -> {
    			Arc arc = builder.addArc(mainPage, mainPageTransition, _targetNode, "");
    			return arc;
    		});
        	transitions.forEach(subPageTransition -> {
        		builder.addArc(subPage.getPage(), subPageTransition, subPage.refPlaceFor((Place) targetNode), annotation);
        	});
        });
        Map<Node, Arc> ingoingArcs = new HashMap<>();
        inputs.forEach((assoc, transitions) -> {
            ItemAwareElement dataObject = assoc.dataElement;
            String annotation = annotationForDataFlow(dataObject, assoc.stateName);
            Node sourceNode = idsToNodes.get(dataObject.getId());
    		ingoingArcs.computeIfAbsent(sourceNode, _sourceNode -> {
    			Arc arc = builder.addArc(mainPage, _sourceNode, mainPageTransition, "");
    			return arc;
    		});
        	transitions.forEach(subPageTransition -> {
        		builder.addArc(subPage.getPage(), subPage.refPlaceFor((Place) sourceNode), subPageTransition, annotation);
        	});
    		/**Assert that when reading and not writing, the unchanged token is put back*/
    		outgoingArcs.computeIfAbsent(idsToNodes.get(dataObject.getId()), targetNode -> {
    			Arc arc = builder.addArc(mainPage, mainPageTransition, targetNode, "");        	
            	transitions.forEach(subPageTransition -> {
            		builder.addArc(subPage.getPage(), subPageTransition, subPage.refPlaceFor((Place) targetNode), annotation);
            	});
    			return arc;
    		});
        });
    }
    
    private String annotationForDataFlow(ItemAwareElement dataObject, Optional<String> stateName) {
        if(dataObject instanceof DataObjectReference) {
            String dataType = trimDataObjectName(((DataObjectReference) dataObject).getDataObject().getName());
            String stateString = stateName.map(x -> ", state = "+x).orElse("");
            return "{id = "+dataObjectId(dataType)+" , caseId = caseId"+stateString+"}";
        } else if (dataObject instanceof  DataStoreReference){
            String dataType = trimDataObjectName(((DataStoreReference) dataObject).getDataStore().getName());
            return dataObjectId(dataType);
        } else {
            return "UNKNOWN";
        }
    }
    
    private void translateGateways() {
        Collection<ExclusiveGateway> exclusiveGateways = bpmn.getModelElementsByType(ExclusiveGateway.class);
        exclusiveGateways.forEach(each -> {
        	Node node = builder.addPlace(mainPage, each.getName(), "CaseID");
        	idsToNodes.put(each.getId(), node);
        });

        Collection<ParallelGateway> parallelGateways = bpmn.getModelElementsByType(ParallelGateway.class);
        parallelGateways.forEach(each -> {        	
        	String name = each.getName();
        	if(name == null || name.equals(""))name = each.getId();
	    	Page gatewayPage = builder.addPage(petriNet, name);
	    	Transition subpageTransition = builder.addTransition(gatewayPage, name);
	        Instance mainPageTransition = builder.createSubPageTransition(gatewayPage, mainPage, name);
	        SubpageElement subPage = new SubpageElement(this, each.getId(), gatewayPage, mainPageTransition, Arrays.asList(subpageTransition));
	        subpages.put(each.getId(), subPage);
	    	idsToNodes.put(each.getId(), mainPageTransition);
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
        			if(Objects.nonNull(subPage)) {
	        			subPage.getSubpageTransitions().forEach(transition -> {
	            			builder.addArc(subPage.getPage(), subPage.refPlaceFor((Place) source), transition, "caseId");
	        			});
        			}
        		}
        		if(!isPlace(source)) {
        			SubpageElement subPage = subpages.get(sourceId);
        			if(Objects.nonNull(subPage)) {
            			subPage.getSubpageTransitions().forEach(transition -> {
                			builder.addArc(subPage.getPage(), transition, subPage.refPlaceFor((Place) target), "caseId");
            			});
        			}
        		}
        	} else {
            	Place place = builder.addPlace(mainPage, null, "CaseID");
            	
            	builder.addArc(mainPage, source, place, "");
       			SubpageElement sourceSubPage = subpages.get(sourceId);
       			if(Objects.nonNull(sourceSubPage)) {
        			sourceSubPage.getSubpageTransitions().forEach(transition -> {
            			builder.addArc(sourceSubPage.getPage(), transition, sourceSubPage.refPlaceFor(place), "caseId");
        			});
       			}

            	builder.addArc(mainPage, place, target, "");
    			SubpageElement targetSubPage = subpages.get(targetId);
    			if(Objects.nonNull(targetSubPage)) {
        			targetSubPage.getSubpageTransitions().forEach(transition -> {
            			builder.addArc(targetSubPage.getPage(), targetSubPage.refPlaceFor(place), transition, "caseId");
        			});
    			}
        	}
        });
    }
    
    public String findKnownParent(String sourceId) {
    	ModelElementInstance element = bpmn.getModelElementById(sourceId);
    	String id = sourceId;
    	while(element != null && !idsToNodes.containsKey(id)) {
    		element = element.getParentElement();
    		id = element.getAttributeValue("id");
    	}
    	if(element != null)return id;
    	else throw new Error("Could not find known parent element for element with id "+sourceId);
		
    }
    
    private static boolean isPlace(Node node) {
    	return node instanceof Place;
    }
    
    private void defer(Runnable r) {
    	deferred.add(r);
    }
    
    private void runDeferredCalls() {
    	deferred.forEach(Runnable::run);
    }
    
    private void layout() {
    }
    
    //=======Generation Methods======
    public Page createPage(String name) {
    	return builder.addPage(petriNet, name);
    }
    
    public Instance createSubpageTransition(String name, Page page) {
    	return builder.createSubPageTransition(page, mainPage, name);
    }
    
    
    //========Accessors======
	public BuildCPNUtil getBuilder() {
		return builder;
	}
	
	public BpmnModelInstance getBpmn() {
		return bpmn;
	}
	
    //========Static========
    public static String normalizeActivityName(String name) {
    	return name.replace('\n', ' ');
    }
    
    public static String dataElementId(ModelElementInstance element) {
        if (element instanceof DataObjectReference) {
            return ((DataObjectReference)element).getDataObject().getName().trim().replaceAll("\\s", "");
        } else {
            return ((DataStoreReference)element).getDataStore().getName().trim().replaceAll("\\s", "");
        }
    }
    
    public static <T extends ModelElementInstance> List<String> getReferenceIds(DataAssociation association, Class<T> referenceClass) {
		return association.getChildElementsByType(referenceClass).stream()
				.map(each -> each.getTextContent())
				.collect(Collectors.toList());
    }
    

}
