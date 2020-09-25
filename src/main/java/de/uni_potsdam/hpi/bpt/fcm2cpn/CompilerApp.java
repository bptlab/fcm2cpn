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

import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.allCombinationsOf;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.dataObjectStateToNetColors;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.getSource;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.getTarget;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.normalizeElementName;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
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
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.ModelFactory;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNList;
import org.cpntools.accesscpn.model.cpntypes.CPNProduct;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import de.uni_potsdam.hpi.bpt.fcm2cpn.TransputSetWrapper.InputSetWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.TransputSetWrapper.OutputSetWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModelParser;


public class CompilerApp {

    public final static String licenseInfo = "fCM2CPN translator  Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam, Germany\n" +
            "This program comes with ABSOLUTELY NO WARRANTY.\n" +
            "This is free software, and you are welcome to redistribute it under certain conditions.\n";
    
    /** The bpmn model to be parsed*/
	private BpmnModelInstance bpmn;
    /** Parsed data model of the bpmn model*/
	private DataModel dataModel;
	
	/** Helper for constructing the resulting net*/
	public final BuildCPNUtil builder;
	/** The resulting petri net*/
	private PetriNet petriNet;
    /** Net page that includes high level subpage transitions for bpmn elements like events, activities and gateways*/
	private Page mainPage;
	/** Maps from bpmn elments to their representations on the {@link #mainPage}*/
	private Map<BaseElement, Node> nodeMap;
	/** Maps sub pages for elements like like events, activities and gateways*/
	private Map<BaseElement, SubpageElement> subpages;
	
	/** Global place for all associations*/
	private Place associationsPlace;
	/** Set of activities that read from {@link #associationsPlace}, to avoid duplicate arcs on main page*/
	private final Set<Activity> associationReaders;
	/** Set of activities that write to {@link #associationsPlace}, to avoid duplicate arcs on main page*/
	private final Set<Activity> associationWriters;
	
	/** Steps that run after (most of) the net is created; used e.g. in {@link #translateBoundaryEvents()} to access all control flow places of an interrupted activity*/
	private List<Runnable> deferred;
	

	
	/** Wrapper for data objects, see {@link DataObjectWrapper}*/
	private Collection<DataObjectWrapper> dataObjectWrappers;

	/** Wrapper for data stores, see {@link DataStoreWrapper}*/
	private Collection<DataStoreWrapper> dataStoreWrappers;

    public static void main(final String[] _args) throws Exception {
        System.out.println(licenseInfo);
        List<String> args = new ArrayList<>(Arrays.asList(_args));
        boolean useDataModel = args.remove("-d");
        
        File bpmnFile;
        if(!args.isEmpty()) {
        	bpmnFile = new File(args.get(0));
        	args.remove(0);
        } else {
        	bpmnFile = getFile();
        }        
        if (null == bpmnFile) {
            System.exit(0);
        }
        
        Optional<File> dataModelFile = Optional.empty();
        if(useDataModel) {
            if(!args.isEmpty()) {
            	dataModelFile = Optional.of(new File(args.get(0)));
            	args.remove(0);
            } else {
            	dataModelFile = Optional.ofNullable(getFile());//TODO adapt file filter
            }       
        } 
        
        Optional<DataModel> dataModel = dataModelFile.map(DataModelParser::parse);
        BpmnModelInstance bpmn = loadBPMNFile(bpmnFile);
        PetriNet petriNet = translateBPMN2CPN(bpmn, dataModel);
		ModelPrinter.printModel(petriNet);
        System.out.print("Writing CPN file... ");
        DOMGenerator.export(petriNet, "./"+bpmnFile.getName().replaceAll("\\.bpmn", "")+".cpn");
        System.out.println("DONE");
    }
    
    public static PetriNet translateBPMN2CPN(BpmnModelInstance bpmn, Optional<DataModel> dataModel) {
    	return new CompilerApp(bpmn, dataModel).translateBPMN2CPN();
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

    private CompilerApp(BpmnModelInstance bpmn, Optional<DataModel> dataModel) {
    	this.bpmn = bpmn;
        this.builder = new BuildCPNUtil();
        this.subpages = new HashMap<>();
        this.nodeMap = new HashMap<>();
        this.deferred = new ArrayList<>();
        this.dataModel = dataModel.orElse(DataModel.none());
        
        this.associationReaders = new HashSet<>();
        this.associationWriters = new HashSet<>();
	}
    
    private static BpmnModelInstance loadBPMNFile(File bpmnFile) {
        System.out.print("Load and parse BPMN file... ");
        BpmnModelInstance bpmn = Bpmn.readModelFromFile(bpmnFile);
        System.out.println("DONE");
        return bpmn;
    }

    private PetriNet translateBPMN2CPN() {
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
        petriNet.setName(ModelFactory.INSTANCE.createName());
        petriNet.getName().setText("Compiled BPMN Model");
        mainPage = createPage("Main Page");
        initializeDefaultColorSets();
        initializeDefaultVariables();
        
        builder.declareMLFunction(petriNet, 
        		"fun filter pred [] = []\n" + 
        		"| filter pred (x::xs) = if pred(x)\n" + 
        		"then x::(filter pred xs)\n" + 
        		"else filter pred xs;");
        
        builder.declareMLFunction(petriNet, 
        		"fun listAssocs sourceId targetClass assoc = \n" + 
        		"filter (fn([(a1, a2), (b1, b2)]) => (\n" + 
        		"(a1,a2)=sourceId andalso b1 = targetClass) \n" + 
        		"orelse ((b1,b2)=sourceId andalso a1 = targetClass)\n" + 
        		") assoc;");
        
        
        System.out.println("DONE");
    }
    
    private void initializeDefaultColorSets() {
        builder.declareStandardColors(petriNet);
        
        builder.declareColorSet(petriNet, "CaseID", CpntypesFactory.INSTANCE.createCPNString());
        builder.declareColorSet(petriNet, "DATA_STORE", CpntypesFactory.INSTANCE.createCPNString());
        
        // Type //TODO move to data object wrapper and use prefix instead of normalized name
        CPNEnum typeEnum = CpntypesFactory.INSTANCE.createCPNEnum();
        Collection<DataObject> dataTypes = bpmn.getModelElementsByType(DataObject.class);
        dataTypes.stream()
        	.map(each -> normalizeElementName(each.getName()))
        	.distinct()
            .forEach(typeEnum::addValue);
        if(dataTypes.isEmpty()) typeEnum.addValue("NO_TYPES");
        builder.declareColorSet(petriNet, "TYPE", typeEnum);
        
        // ID 
        CPNProduct id = CpntypesFactory.INSTANCE.createCPNProduct();
        id.addSort("TYPE");
        id.addSort("INT");
        builder.declareColorSet(petriNet, "ID", id);
        
        // State
        CPNEnum stateEnum = CpntypesFactory.INSTANCE.createCPNEnum();
        Collection<DataState> dataStates = bpmn.getModelElementsByType(DataState.class);
        dataStates.stream()
        	.flatMap(state -> dataObjectStateToNetColors(state.getName()))
            .forEach(stateEnum::addValue);
        if(!dataStates.isEmpty())builder.declareColorSet(petriNet, "STATE", stateEnum);
        
        // DataObject
        CPNRecord dataObject = CpntypesFactory.INSTANCE.createCPNRecord();
        dataObject.addValue("id", "ID");
        dataObject.addValue(caseId(), "STRING");
        if(!dataStates.isEmpty())dataObject.addValue("state", "STATE");
        builder.declareColorSet(petriNet, "DATA_OBJECT", dataObject);
        
        //Association & ListOfAssociation
        CPNList association = CpntypesFactory.INSTANCE.createCPNList();
        association.setSort("ID");
        builder.declareColorSet(petriNet, "ASSOCIATION", association);

        CPNList listOfAssociation = CpntypesFactory.INSTANCE.createCPNList();
        listOfAssociation.setSort("ASSOCIATION");
        builder.declareColorSet(petriNet, "LIST_OF_ASSOCIATION", listOfAssociation);
    }
    
    private void initializeDefaultVariables() {
    	createVariable("count", "INT");
    	createVariable(caseId(), "CaseID");
    	createVariable("assoc", "LIST_OF_ASSOCIATION");
    }
    
    private void translateData() {
        translateDataObjects();        
        translateDataStores();
        createAssociationPlace();
    }
    
    
    private void translateDataObjects() {
        Map<String, DataObjectWrapper> dataObjectsNamesToWrappers = new HashMap<>();

    	Collection<DataObject> dataObjects = bpmn.getModelElementsByType(DataObject.class);
        dataObjects.forEach(each -> dataObjectsNamesToWrappers
        		.computeIfAbsent(normalizeElementName(each.getName()), normalizedName -> new DataObjectWrapper(this, normalizedName))
        		.addMappedElement(each));
        
        Collection<DataObjectReference> dataObjectRefs = bpmn.getModelElementsByType(DataObjectReference.class);
        dataObjectRefs.forEach(each -> dataObjectsNamesToWrappers
        		.get(normalizeElementName(each.getDataObject().getName()))
        		.addMappedReference(each));
        
        dataObjectWrappers = dataObjectsNamesToWrappers.values();
    }
    
    private void translateDataStores() {
        Map<String, DataStoreWrapper> dataStoreNamesToWrappers = new HashMap<>();
        
        Collection<DataStore> dataStores = bpmn.getModelElementsByType(DataStore.class);
        dataStores.forEach(each -> dataStoreNamesToWrappers
        		.computeIfAbsent(normalizeElementName(each.getName()), normalizedName -> new DataStoreWrapper(this, normalizedName))
        		.addMappedElement(each));
        Collection<DataStoreReference> dataStoreRefs = bpmn.getModelElementsByType(DataStoreReference.class);
        dataStoreRefs.forEach(each -> dataStoreNamesToWrappers
        		.get(normalizeElementName(each.getDataStore().getName()))
        		.addMappedReference(each));
        
        dataStoreWrappers = dataStoreNamesToWrappers.values();
    }
    
    private void createAssociationPlace() {
    	associationsPlace = createPlace("associations", "LIST_OF_ASSOCIATION", "[]");
    }
    
    private void translateActivities() {
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.forEach(activity -> {
        	String name = activity.getName();
        	Page activityPage = createPage(normalizeElementName(name));
            Instance mainPageTransition = createSubpageTransition(name, activityPage);
            SubpageElement subPage = new SubpageElement(this, activity.getId(), activityPage, mainPageTransition, new ArrayList<>());
            List<Transition> subpageTransitions = subPage.getSubpageTransitions();
            subpages.putIfAbsent(activity, subPage);
            nodeMap.put(activity, mainPageTransition);
            
            // All possible combinations of input and output sets, either defined by io-specification or *all* possible combinations are used
            List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets = transputSets(activity);
            
            
            if(transputSets.isEmpty()) {
                // All read data elements with all states in that they are read
                Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataInputAssociation>>> inputsPerObject = activity.getDataInputAssociations().stream()
                		.flatMap(this::splitDataAssociationByState)
                		.collect(Collectors.groupingBy(this::wrapperFor));

                // All written data elements with all states in that they are written
                Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataOutputAssociation>>> outputsPerObject = activity.getDataOutputAssociations().stream()
                		.flatMap(this::splitDataAssociationByState)
                		.collect(Collectors.groupingBy(this::wrapperFor));
                List<InputSetWrapper> inputSets = allCombinationsOf(inputsPerObject.values()).stream().map(InputSetWrapper::new).collect(Collectors.toList());
                List<OutputSetWrapper> outputSets = allCombinationsOf(outputsPerObject.values()).stream().map(OutputSetWrapper::new).collect(Collectors.toList());
            	for (InputSetWrapper inputSet : inputSets) {
                	for (OutputSetWrapper outputSet : outputSets) {
                		transputSets.add(new Pair<>(inputSet, outputSet));
                	}
                }
            }

            Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputtingTransitions = activity.getDataOutputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
            Map<StatefulDataAssociation<DataInputAssociation>, List<Transition>> inputtingTransitions = activity.getDataInputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
            
            int transputSetIndex = 0;
            for (Pair<InputSetWrapper, OutputSetWrapper> transputSet : transputSets) {
                InputSetWrapper inputSet = transputSet.first;
                OutputSetWrapper outputSet = transputSet.second;

                Set<DataObjectWrapper> readObjects = inputSet.stream()
                		.map(this::wrapperFor)
                        .filter(DataElementWrapper::isDataObjectWrapper)
                        .map(DataObjectWrapper.class::cast)
                        .collect(Collectors.toSet());
                Set<DataObjectWrapper> writtenObjects = outputSet.stream()
                		.map(this::wrapperFor)
                        .filter(DataElementWrapper::isDataObjectWrapper)
                        .map(DataObjectWrapper.class::cast)
                        .collect(Collectors.toSet());
                Set<DataObjectWrapper> createdObjects = writtenObjects.stream()
                        .filter(object -> !readObjects.contains(object))
                        .collect(Collectors.toSet());

                Transition subpageTransition = builder.addTransition(activityPage, name + "_" + transputSetIndex);
                subpageTransitions.add(subpageTransition);
                attachObjectCreationCounters(subpageTransition, createdObjects);
                
                associateDataObjects(activity, subpageTransition, readObjects, writtenObjects);
            	
                inputSet.forEach(input -> inputtingTransitions.get(input).add(subpageTransition));
                outputSet.forEach(output -> outputtingTransitions.get(output).add(subpageTransition));
                
                transputSetIndex++;
            }
            createDataAssociationArcs(activity, outputtingTransitions, inputtingTransitions);
            
        });
    }
    
    private List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets(Activity activity) {
        List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets = new ArrayList<>();
        if(activity.getIoSpecification() == null) return transputSets;
        
        // Output sets mapped to data assocs and split by "|" state shortcuts
    	Map<OutputSet, List<OutputSetWrapper>> translatedOutputSets = activity.getIoSpecification().getOutputSets().stream().collect(Collectors.toMap(Function.identity(), outputSet -> {
            Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataOutputAssociation>>> outputsPerObject = outputSet.getDataOutputRefs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.groupingBy(this::wrapperFor));
            return allCombinationsOf(outputsPerObject.values()).stream().map(OutputSetWrapper::new).collect(Collectors.toList());
        }));
    	
        activity.getIoSpecification().getInputSets().forEach(inputSet -> {
            Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataInputAssociation>>> inputsPerObject = inputSet.getDataInputs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.groupingBy(this::wrapperFor));
            List<InputSetWrapper> translatedInputSets = allCombinationsOf(inputsPerObject.values()).stream().map(InputSetWrapper::new).collect(Collectors.toList());
            for(OutputSet outputSet : inputSet.getOutputSets()) {
            	for(OutputSetWrapper translatedOutputSet : translatedOutputSets.get(outputSet)) {
                	for(InputSetWrapper translatedInputSet : translatedInputSets) {
                		transputSets.add(new Pair<>(translatedInputSet, translatedOutputSet));
                	}
            	}
            }
        });
        
        //Data Stores are (at least in Signavio) not part of input or output sets
        List<StatefulDataAssociation<DataInputAssociation>> dataStoreInputs = activity.getDataInputAssociations().stream()
			   .filter(assoc -> getSource(assoc) instanceof DataStoreReference)
			   .flatMap(this::splitDataAssociationByState)
			   .collect(Collectors.toList());

        List<StatefulDataAssociation<DataOutputAssociation>> dataStoreOutputs = activity.getDataOutputAssociations().stream()
 			   .filter(assoc -> getTarget(assoc) instanceof DataStoreReference)
 			   .flatMap(this::splitDataAssociationByState)
 			   .collect(Collectors.toList());
        
        for(Pair<InputSetWrapper, OutputSetWrapper> transputSet : transputSets) {
        	transputSet.first.addAll(dataStoreInputs);
        	transputSet.second.addAll(dataStoreOutputs);
        }
    
    	return transputSets;
    }

	private void attachObjectCreationCounters(Transition transition, Set<DataObjectWrapper> createObjects) {
		if(createObjects.isEmpty()) return;
        String countVariables = createObjects.stream().map(DataObjectWrapper::dataElementCount).collect(Collectors.joining(",\n"));
        String idVariables = createObjects.stream().map(DataObjectWrapper::dataElementId).collect(Collectors.joining(",\n"));
        String idGeneration = createObjects.stream().map(object -> "("+object.namePrefix() + ", " + object.dataElementCount() +")").collect(Collectors.joining(",\n"));
        Page page = transition.getPage();
        transition.getCode().setText(String.format(
                "input (%s);\n"
                        + "output (%s);\n"
                        + "action (%s);",
                countVariables,
                idVariables,
                idGeneration));
        createObjects.forEach(object -> {
            PlaceNode caseTokenPlace = object.creationCounterForPage(page);
            builder.addArc(page, caseTokenPlace, transition, object.dataElementCount());
            builder.addArc(page, transition, caseTokenPlace, object.dataElementCount() + "+ 1");
        });
    }
    
    private void translateEvents() {
    	translateStartEvents();
    	translateEndEvents();
    	translateBoundaryEvents();
    }

	private void translateStartEvents() {
        Collection<StartEvent> events = bpmn.getModelElementsByType(StartEvent.class);
        events.forEach(each -> {
        	String name = elementName(each);
        	Page eventPage = createPage(normalizeElementName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance mainPageTransition = createSubpageTransition(name, eventPage);
        	nodeMap.put(each, mainPageTransition);
            SubpageElement subPage = new SubpageElement(this, each.getId(), eventPage, mainPageTransition, Arrays.asList(subpageTransition));
            subpages.put(each, subPage);
            
            Place caseTokenPlace = createPlace(eventPage, "Case Count", "INT", "1`0");
            builder.addArc(eventPage, caseTokenPlace, subpageTransition, "count");
            builder.addArc(eventPage, subpageTransition, caseTokenPlace, "count + 1");

            
            List<StatefulDataAssociation<DataOutputAssociation>> outputs = each.getDataOutputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
            		.collect(Collectors.toList());

            List<DataElementWrapper<?,?>> createdDataElements = outputs.stream()
            		.map(this::wrapperFor)
                    .distinct()
            		.collect(Collectors.toList());
            
            /* 
             * TODO all data outputs of event are using case count, should dedicated counters be created
             * Use Case: When the input event creates a data object that can also be created by a task
             * Then they should both use the same counter, one for the data object, and not the case counter
             */            
            String idVariables = "caseId, "+createdDataElements.stream().map(DataElementWrapper::dataElementId).collect(Collectors.joining(", "));
            String idGeneration = "String.concat[\"case\", Int.toString(count)]"+createdDataElements.stream().map(n -> ",\n("+n.namePrefix() + ", count)").collect(Collectors.joining(""));
            subpageTransition.getCode().setText(String.format(
            	"input (count);\n"
	            +"output (%s);\n"
    			+"action (%s);",
                idVariables,
                idGeneration));
            
            Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputTransitions = new HashMap<>();
            outputs.forEach(assoc -> outputTransitions.put(assoc, Arrays.asList(subpageTransition)));
        	createDataAssociationArcs(each, outputTransitions, Collections.emptyMap());
        });
    }
    
    private void translateEndEvents() {
        Collection<EndEvent> events = bpmn.getModelElementsByType(EndEvent.class);
        events.forEach(each -> {
        	nodeMap.put(each, createPlace(elementName(each), "CaseID"));
        });
    }

    
    private void translateBoundaryEvents() {
    	//TODO only interrupting is supported
    	
    	Collection<BoundaryEvent> events = bpmn.getModelElementsByType(BoundaryEvent.class);
        events.forEach(each -> {
        	//Must be called before control flow arcs and places are created, because it needs to have the outgoing control flow created
            String name = elementName(each);
        	Page eventPage = createPage(normalizeElementName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance mainPageTransition = createSubpageTransition(name, eventPage);
            SubpageElement subPage = new SubpageElement(this, each.getId(), eventPage, mainPageTransition, Arrays.asList(subpageTransition));
            subpages.put(each, subPage);
        	nodeMap.put(each, mainPageTransition);

		    //Must be called after control flow places are created, to know which will be there and which to consume
        	defer(() -> {
    			Node attachedNode = nodeFor(each.getAttachedTo());
    			assert !isPlace(attachedNode);
    			attachedNode.getTargetArc().stream()
    				.map(arc -> arc.getPlaceNode())
    				.filter(place -> place.getSort().getText().equals("CaseID"))
    				.forEach(place -> {
    					builder.addArc(eventPage, subPage.refPlaceFor((Place) place), subpageTransition, caseId());
    					builder.addArc(mainPage, place, mainPageTransition, "");
    				});
        	});
        });
	}
    
    private <T extends DataAssociation> Stream<StatefulDataAssociation<T>> splitDataAssociationByState(T assoc) {
    	BaseElement source = getSource(assoc);
    	BaseElement target = getTarget(assoc);
        ItemAwareElement dataElement = (ItemAwareElement) (source instanceof DataObjectReference || source instanceof DataStoreReference ? source : target);
        assert dataElement != null;
    	Stream<String> possibleStates = Optional.ofNullable(dataElement.getDataState())
        		.map(DataState::getName)
        		.map(stateName -> dataObjectStateToNetColors(stateName))
        		.orElse(Stream.of((String)null));
        return possibleStates.map(state -> new StatefulDataAssociation<>(assoc, state, dataElement));
    }
    
    /**
     * Creates arcs for all data associations of one element, for all transitions of that element
     * @param element: The element that writes or reads, usually an activity or event
     * @param outputs: For each stateful data assoc: Which (inputset x outputset)-Transitions write this data object in this state
     * @param inputs: For each stateful data assoc: Which (inputset x outputset)-Transitions read this data object in this state
     */
    private void createDataAssociationArcs(BaseElement element, Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputs, Map<StatefulDataAssociation<DataInputAssociation>, List<Transition>> inputs) {
    	Set<DataElementWrapper<?,?>> readElements = inputs.keySet().stream().map(this::wrapperFor).collect(Collectors.toSet());
    	Set<DataElementWrapper<?,?>> writtenElements = outputs.keySet().stream().map(this::wrapperFor).collect(Collectors.toSet());
    	
    	
        outputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = wrapperFor(assoc);
        	String annotation = dataElement.annotationForDataFlow(assoc.getStateName());
        	linkWritingTransitions(element, dataElement, annotation, transitions);
    		/**Assert that when writing a data store and not reading, the token read before*/
        	if(!readElements.contains(dataElement) && dataElement.isDataStoreWrapper()) {
        		linkReadingTransitions(element, dataElement, annotation, transitions);
            	readElements.add(dataElement);
        	}
        });
        
        inputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = wrapperFor(assoc);
            String annotation = dataElement.annotationForDataFlow(assoc.getStateName());
    		linkReadingTransitions(element, dataElement, annotation, transitions);

    		/**Assert that when reading and not writing, the unchanged token is put back*/
    		List<Transition> readOnlyTransitions = transitions.stream()
    				.filter(transition -> outputs.entrySet().stream().noneMatch(entry -> wrapperFor(entry.getKey()).equals(dataElement) && entry.getValue().contains(transition)))
    				.collect(Collectors.toList());
    		linkWritingTransitions(element, dataElement, annotation, readOnlyTransitions);
        });
    }
    
    private void linkWritingTransitions(BaseElement element, DataElementWrapper<?,?> dataElement, String annotation, List<Transition> transitions) {
    	SubpageElement subPage = subpages.get(element);
    	dataElement.assertMainPageArcFrom(element);
    	transitions.forEach(subPageTransition -> {
    		builder.addArc(subPageTransition.getPage(), subPageTransition, subPage.refPlaceFor(dataElement.place), annotation);
    	});
    }    
    
    private void linkReadingTransitions(BaseElement element, DataElementWrapper<?,?> dataElement, String annotation, List<Transition> transitions) {
    	SubpageElement subPage = subpages.get(element);
    	dataElement.assertMainPageArcTo(element);
    	transitions.forEach(subPageTransition -> {
    		builder.addArc(subPageTransition.getPage(), subPage.refPlaceFor(dataElement.place), subPageTransition, annotation);
    	});
    }
    

    
    private void associateDataObjects(Activity activity, Transition transition, Set<DataObjectWrapper> readDataObjects, Set<DataObjectWrapper> writtenDataObjects) {
    	SubpageElement activityWrapper = subpages.get(activity);
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToWrite = new HashSet<>();
		
		
		for(DataObjectWrapper writtenObject : writtenDataObjects) {
			for(DataObjectWrapper readObject : readDataObjects) {
				if(!writtenObject.equals(readObject) && dataModel.isAssociated(writtenObject.getNormalizedName(), readObject.getNormalizedName())) {
					associationsToWrite.add(new Pair<>(writtenObject, readObject));
				}
			}
			for(DataObjectWrapper otherWrittenObject : writtenDataObjects) {
				if(!writtenObject.equals(otherWrittenObject) && dataModel.isAssociated(writtenObject.getNormalizedName(), otherWrittenObject.getNormalizedName())) {
					associationsToWrite.add(new Pair<>(writtenObject, otherWrittenObject));
				}
			}
		}
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkedAssociations = checkAssociationsOfReadDataObjects(transition, readDataObjects);
		//Write all read associations back (they are not consumed), but check for duplicates with created associations (therefore use set of assoc)
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> allAssocations = new HashSet<>();
		allAssocations.addAll(checkedAssociations);
		allAssocations.addAll(associationsToWrite);
		
		//If either new assocs are created or old assocs are checked, we need arcs from and to the assoc place
		if(!allAssocations.isEmpty()) {
			// Create reading arcs
			String readAnnotation = "assoc";
			activityWrapper.createArcFrom(associationsPlace, transition, readAnnotation);
			if(!associationReaders.contains(activity)) {
				createArc(associationsPlace, nodeFor(activity));
				associationReaders.add(activity);
			}
			
			//Create write back arcs; if new assocs are create, write the union back; if assocs are checked, they already exist
			String writeAnnotation = "assoc";
			associationsToWrite.removeAll(checkedAssociations);
			if(!associationsToWrite.isEmpty()) {
				
				writeAnnotation = "union assoc "+associationsToWrite.stream()
					.map(assoc -> Stream.of(assoc.first, assoc.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString())
					.distinct()
					.collect(Collectors.toList())
					.toString();
				associationsToWrite.forEach(pair -> {
					Association assoc = dataModel.getAssociation(pair.first.getNormalizedName(), pair.second.getNormalizedName()).get();
					//Create guards for: Cannot create data object if this would violate upper bounds
					Stream.of(pair.first, pair.second).forEach(dataObject -> {
						AssociationEnd end = assoc.getEnd(dataObject.getNormalizedName());
						int limit = end.getUpperBound();
						if(limit > 1 && limit != AssociationEnd.UNLIMITED) {
							String existingGuard = transition.getCondition().asString();
							if(!existingGuard.isEmpty()) existingGuard += "\nandalso ";
							DataObjectWrapper otherObject = (DataObjectWrapper) pair.otherElement(dataObject);
							String newGuard = "(length (listAssocs "+otherObject.dataElementId()+" "+dataObject.namePrefix()+" assoc) < "+limit+")";
							transition.getCondition().setText(existingGuard+newGuard);
						}
					});
					
					//Create guards for: Cannot create data object if lower bounds of read list data object are not given
					//TODO duplicate
					Stream.of(pair.first, pair.second).forEach(dataObject -> {
						DataObjectWrapper otherObject = (DataObjectWrapper) pair.otherElement(dataObject);
						AssociationEnd end = assoc.getEnd(otherObject.getNormalizedName());
						int limit = end.getLowerBound();
						if(limit > 1) {
							Set<DataObjectWrapper> potentialIdentifiers = associationsToWrite.stream()
								.filter(otherAssoc -> otherAssoc.contains(dataObject) && !otherAssoc.contains(otherObject))
								.flatMap(otherAssoc -> {
									DataObjectWrapper potentialIdentifier = (DataObjectWrapper) otherAssoc.otherElement(dataObject);
									return dataModel.getAssociation(potentialIdentifier.getNormalizedName(), otherObject.getNormalizedName())
										.filter(identifyingAssoc -> identifyingAssoc.getEnd(potentialIdentifier.normalizedName).getUpperBound() <= 1)
										.map(identifyingAssoc -> potentialIdentifier)
										.stream();
					
								}).collect(Collectors.toSet());
							assert potentialIdentifiers.size() == 1;
							DataObjectWrapper identifier = potentialIdentifiers.stream().findAny().get();
							
							String existingGuard = transition.getCondition().asString();
							if(!existingGuard.isEmpty()) existingGuard += "\nandalso ";
							String newGuard = "(length (listAssocs "+identifier.dataElementId()+" "+otherObject.namePrefix()+" assoc) >= "+limit+")";
							transition.getCondition().setText(existingGuard+newGuard);
						}
					});
				});
				
				
			}
			activityWrapper.createArcTo(associationsPlace, transition, writeAnnotation);
			if(!associationWriters.contains(activity)) {
				createArc(nodeFor(activity), associationsPlace);
				associationWriters.add(activity);
			}
		}
    }
    
    private Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkAssociationsOfReadDataObjects(Transition transition, Set<DataObjectWrapper> readDataObjects) {
    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToCheck = new HashSet<>();
		
		for(DataObjectWrapper readObject : readDataObjects) {
			for(DataObjectWrapper otherReadObject : readDataObjects) {
				if(!readObject.equals(otherReadObject) && dataModel.isAssociated(readObject.getNormalizedName(), otherReadObject.getNormalizedName())) {
					associationsToCheck.add(new Pair<>(readObject, otherReadObject));
				}
			}
		}
		
//		associationsToCheck.forEach(assoc -> {
//			String annotation = assoc.stream().map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString();
//			for(Transition transition : transitions) {
//	    		createArc(subPage.getPage(), subPage.refPlaceFor(associationsPlace), transition, annotation);
//			}
//		});
		if(!associationsToCheck.isEmpty()) {
//			String annotation = associationsToCheck.stream()
//				.map(assoc -> "1`"+assoc.stream().map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString())
//				.collect(Collectors.joining("++\n"));
			String guard = "contains assoc "+ associationsToCheck.stream()
				.map(assoc -> Stream.of(assoc.first, assoc.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString())
				.distinct()
				.collect(Collectors.toList())
				.toString();
			transition.getCondition().setText(guard);
		}
		return associationsToCheck;
    }
    
    
    private void translateGateways() {
        Collection<ExclusiveGateway> exclusiveGateways = bpmn.getModelElementsByType(ExclusiveGateway.class);
        exclusiveGateways.forEach(each -> {
        	String name = elementName(each);
        	Node node = createPlace(name, "CaseID");
        	nodeMap.put(each, node);
        });

        Collection<ParallelGateway> parallelGateways = bpmn.getModelElementsByType(ParallelGateway.class);
        parallelGateways.forEach(each -> {        	
        	String name = elementName(each);
	    	Page gatewayPage = createPage(name);
	    	Transition subpageTransition = builder.addTransition(gatewayPage, name);
	        Instance mainPageTransition = createSubpageTransition(name, gatewayPage);
	        SubpageElement subPage = new SubpageElement(this, each.getId(), gatewayPage, mainPageTransition, Arrays.asList(subpageTransition));
	        subpages.put(each, subPage);
	    	nodeMap.put(each, mainPageTransition);
        });
    }
    
    private void translateControlFlow() {
        Collection<SequenceFlow> sequenceFlows = bpmn.getModelElementsByType(SequenceFlow.class);
        sequenceFlows.forEach(each -> {
        	FlowNode sourceNode = each.getSource();
        	FlowNode targetNode = each.getTarget();
        	Node source = nodeFor(sourceNode);
        	Node target = nodeFor(targetNode);
        	//System.out.println(source.getName().asString()+" -> "+target.getName().asString());
        	if(isPlace(source) && isPlace(target)) {
        		Transition transition = builder.addTransition(mainPage, null);
        		builder.addArc(mainPage, source, transition, caseId());
        		builder.addArc(mainPage, transition, target, caseId());
        		
        	} else if(isPlace(source) || isPlace(target)) {
        		builder.addArc(mainPage, source, target, "");
        		if(subpages.containsKey(targetNode)) {
        			subpages.get(targetNode).createArcsFrom((Place) source, caseId());
        		}
        		if(subpages.containsKey(sourceNode)) {
        			subpages.get(sourceNode).createArcsTo((Place) target, caseId());
        		}
        		
        	} else {
            	Place place = createPlace(null, "CaseID");
            	
            	builder.addArc(mainPage, source, place, "");
       			if(subpages.containsKey(sourceNode)) {
           			subpages.get(sourceNode).createArcsTo(place, caseId());
       			}

            	builder.addArc(mainPage, place, target, "");
    			if(subpages.containsKey(targetNode)) {
        			subpages.get(targetNode).createArcsFrom(place, caseId());
    			}
        	}
        });
    }
    
    public ItemAwareElement findParentDataElement(String sourceId) {
    	ModelElementInstance element = bpmn.getModelElementById(sourceId);
    	while(element != null && !(element instanceof ItemAwareElement)) {
    		element = element.getParentElement();
    	}
    	if(element != null)return (ItemAwareElement) element;
    	return null;
		
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
    	//TODO
    }
    
    private DataElementWrapper<?,?> wrapperFor(StatefulDataAssociation<?> assoc) {
    	return Stream.concat(dataObjectWrappers.stream(), dataStoreWrappers.stream())
    			.filter(any -> any.isForReference(assoc.getDataElement())).findAny().get();
    }    
    
    
    //=======Generation Methods======
    public Page createPage(String name) {
    	return builder.addPage(petriNet, name);
    }
    
    public Place createPlace(String name, String type) {
    	return builder.addPlace(mainPage, name, type);
    }
    
    public Place createPlace(String name, String type, String initialMarking) {
    	return builder.addPlace(mainPage, name, type, initialMarking);
    }
    
    public Place createPlace(Page page, String name, String type, String initialMarking) {
    	return builder.addPlace(page, name, type, initialMarking);
    }
    
    public RefPlace createFusionPlace(Page page, String name, String type, String initialMarking) {
    	return builder.addFusionPlace(page, name, type, initialMarking, name);
    }
    
    public Instance createSubpageTransition(String name, Page page) {
    	return builder.createSubPageTransition(page, mainPage, name);
    }
    
    public Arc createArc(Page page, Node source, Node target, String annotation) {
    	return builder.addArc(page, source, target, annotation);
    }
    
    public Arc createArc(Node source, Node target) {
    	return createArc(mainPage, source, target, "");
    }
    
    public void createVariable(String name, String type) {
        builder.declareVariable(petriNet, name, type);
    }
    
    
    //========Accessors======
	public BuildCPNUtil getBuilder() {
		return builder;
	}
	
	public BpmnModelInstance getBpmn() {
		return bpmn;
	}
	
	public DataModel getDataModel() {
		return dataModel;
	}

	public Node nodeFor(BaseElement element) {
		return nodeMap.get(element);
	}
	
	public String caseId() {
		return "caseId";
	}

}
