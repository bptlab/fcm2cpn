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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
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
	public final BuildCPNUtil builder;
	private PetriNet petriNet;
	private Map<BaseElement, SubpageElement> subpages;
	private Map<BaseElement, Node> nodeMap;
	
	private Place associationsPlace;
	private final Set<Activity> associationReaders;
	private final Set<Activity> associationWriters;
	
	private List<Runnable> deferred;
	
	private DataElementWrapper<ItemAwareElement, Object> caseWrapper = new DataElementWrapper<ItemAwareElement, Object>(this, "case") {
		@Override
		protected Place createPlace() {
			return null;
		}

		@Override
		public String annotationForDataFlow(Optional<String> stateName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isDataObjectWrapper() {
			return false;
		}

		@Override
		public boolean isDataStoreWrapper() {
			return false;
		}
	};
	private Collection<DataObjectWrapper> dataObjectWrappers;
	private Collection<DataStoreWrapper> dataStoreWrappers;

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
        PetriNet petriNet = translateBPMN2CPN(bpmn);
		ModelPrinter.printModel(petriNet);
        System.out.print("Writing CPN file... ");
        DOMGenerator.export(petriNet, "./"+bpmnFile.getName().replaceAll("\\.bpmn", "")+".cpn");
        System.out.println("DONE");
    }
    
    public static PetriNet translateBPMN2CPN(BpmnModelInstance bpmn) {
    	return new CompilerApp(bpmn).translateBPMN2CPN();
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
        this.nodeMap = new HashMap<>();
        this.deferred = new ArrayList<>();
        this.dataModel = new DataModel();
        
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
        System.out.println("DONE");
    }
    
    private void initializeDefaultColorSets() {
        builder.declareStandardColors(petriNet);
        builder.declareColorSet(petriNet, "CaseID", CpntypesFactory.INSTANCE.createCPNString());
        builder.declareColorSet(petriNet, "DATA_STORE", CpntypesFactory.INSTANCE.createCPNString());
        
        CPNEnum cpnEnum = CpntypesFactory.INSTANCE.createCPNEnum();
        Collection<DataState> dataStates = bpmn.getModelElementsByType(DataState.class);
        dataStates.stream()
        	.flatMap(state -> dataObjectStateToNetColors(state.getName()))
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
    	createVariable("count", "INT");
    	createVariable("caseId", "CaseID");
    	createVariable("assoc", "ASSOCIATION");
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
    	associationsPlace = createPlace("associations", "ASSOCIATION");
    }
    
    private void translateActivities() {
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.forEach(each -> {
        	String name = each.getName();
        	Page activityPage = createPage(normalizeElementName(name));
            Instance mainPageTransition = createSubpageTransition(name, activityPage);
            SubpageElement subPage = new SubpageElement(this, each.getId(), activityPage, mainPageTransition, new ArrayList<>());
            List<Transition> subpageTransitions = subPage.getSubpageTransitions();
            subpages.putIfAbsent(each, subPage);
            nodeMap.put(each, mainPageTransition);
            Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataInputAssociation>>> inputsPerObject = each.getDataInputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
                    .collect(Collectors.toConcurrentMap(
                    		assoc ->  wrapperFor(assoc.dataElement),
                    		Arrays::asList,
		                    (a,b) -> {
		                        a = new ArrayList<>(a);
		                        a.addAll(b);
		                        return a;
		                    }
                    ));
            Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataOutputAssociation>>> outputsPerObject = each.getDataOutputAssociations().stream()
            		.flatMap(this::splitDataAssociationByState)
                    .collect(Collectors.toConcurrentMap(
                    		assoc ->  wrapperFor(assoc.dataElement),
                    		Arrays::asList,
		                    (a,b) -> {
		                        a = new ArrayList<>(a);
		                        a.addAll(b);
		                        return a;
		                    }
                    ));
            
            List<List<StatefulDataAssociation<DataInputAssociation>>> inputSets = allCombinationsOf(inputsPerObject.values());
            List<List<StatefulDataAssociation<DataOutputAssociation>>> outputSets = allCombinationsOf(outputsPerObject.values());

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
                    Set<DataObjectWrapper> dataInputs = inputSet.stream()
                            .map(assoc -> assoc.dataElement)
                            .filter(object -> object instanceof DataObjectReference)
                            .map(DataObjectReference.class::cast)
                            .map(this::wrapperFor)
                            .collect(Collectors.toSet());
                    Set<DataObjectWrapper> createObjects = outputSet.stream()
                            .map(assoc -> assoc.dataElement)
                            .filter(object -> object instanceof DataObjectReference)
                            .map(DataObjectReference.class::cast)
                            .map(this::wrapperFor)
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
            translateDataAssociations(each, outputs, inputs);
            associateWrittenDataObjects(each, inputsPerObject.keySet(), outputsPerObject.keySet(), subpageTransitions);
            checkAssociationsOfReadDataObjects(each, inputsPerObject.keySet(), subpageTransitions);
        });
    }

	private void attachObjectCreationCounters(Transition transition, Set<DataObjectWrapper> createObjects) {
        String countVariables = createObjects.stream().map(DataObjectWrapper::dataElementCount).collect(Collectors.joining(",\n"));
        String idVariables = createObjects.stream().map(DataObjectWrapper::dataElementId).collect(Collectors.joining(",\n"));
        String idGeneration = createObjects.stream().map(object -> "String.concat[\"" + object.namePrefix() + "\", Int.toString(" + object.dataElementCount() +")]").collect(Collectors.joining(",\n"));
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

            /* 
             * TODO all data outputs of event are using case count, should dedicated counters be created
             * Use Case: When the input event creates a data object that can also be created by a task
             * Then they should both use the same counter, one for the data object, and not the case counter
             */
            List<DataElementWrapper<?,?>> ids = each.getDataOutputAssociations().stream()
                    .map(assoc -> {
                        ItemAwareElement dataObject = findParentDataElement(getReferenceIds(assoc, TargetRef.class).get(0));
                        return wrapperFor(dataObject);
                    })
                    .distinct()
                    .collect(Collectors.toList());
            ids.add(0, caseWrapper);
            
            String idVariables = ids.stream().map(DataElementWrapper::dataElementId).collect(Collectors.joining(", "));
            String idGeneration = ids.stream().map(n -> "String.concat[\"" + n.namePrefix() + "\", Int.toString(count)]").collect(Collectors.joining(",\n"));
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
        	translateDataAssociations(each, outputs, Collections.emptyMap());
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
        	String name = elementName(each);
        	Page eventPage = createPage(normalizeElementName(name));
        	Transition subpageTransition = builder.addTransition(eventPage, name);
            Instance mainPageTransition = createSubpageTransition(name, eventPage);
            SubpageElement subPage = new SubpageElement(this, each.getId(), eventPage, mainPageTransition, Arrays.asList(subpageTransition));
            subpages.put(each, subPage);
        	nodeMap.put(each, mainPageTransition);
            
        	Node attachedNode = nodeMap.get(each.getAttachedTo());
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
    	ItemAwareElement source = findParentDataElement(getReferenceIds(assoc, SourceRef.class).get(0));
    	ItemAwareElement target = findParentDataElement(getReferenceIds(assoc, TargetRef.class).get(0));
        ItemAwareElement dataElement = source instanceof DataObjectReference || source instanceof DataStoreReference ? source : target;
        assert dataElement != null;
    	Stream<String> possibleStates = Optional.ofNullable(dataElement.getDataState())
        		.map(DataState::getName)
        		.map(stateName -> dataObjectStateToNetColors(stateName))
        		.orElse(Stream.of((String)null));
        return possibleStates.map(state -> new StatefulDataAssociation<>(assoc, state, dataElement));
    }
    
    /**
     * 
     * @param element
     * @param outputs: For each stateful data assoc: Which (inputset x outputset)-Transitions write this data object in this state
     * @param inputs: For each stateful data assoc: Which (inputset x outputset)-Transitions read this data object in this state
     */
    private void translateDataAssociations(BaseElement element, Map<StatefulDataAssociation<DataOutputAssociation>, List<Transition>> outputs, Map<StatefulDataAssociation<DataInputAssociation>, List<Transition>> inputs) {
    	Set<DataElementWrapper<?,?>> readElements = inputs.keySet().stream().map(assoc -> assoc.dataElement).map(this::wrapperFor).collect(Collectors.toSet());
    	Set<DataElementWrapper<?,?>> writtenElements = outputs.keySet().stream().map(assoc -> assoc.dataElement).map(this::wrapperFor).collect(Collectors.toSet());
    	
        outputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = wrapperFor(assoc.dataElement);
        	String annotation = dataElement.annotationForDataFlow(assoc.stateName);
        	linkWritingTransitions(element, dataElement, annotation, transitions);
    		/**Assert that when writing a data store and not reading, the token read before*/
        	if(!readElements.contains(dataElement) && dataElement.isDataStoreWrapper()) {
        		linkReadingTransitions(element, dataElement, annotation, transitions);
            	readElements.add(dataElement);
        	}
        });
        
        inputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = wrapperFor(assoc.dataElement);
            String annotation = dataElement.annotationForDataFlow(assoc.stateName);
    		linkReadingTransitions(element, dataElement, annotation, transitions);
    		/**Assert that when reading and not writing, the unchanged token is put back*/
        	if(!writtenElements.contains(dataElement)) {
        		linkWritingTransitions(element, dataElement, annotation, transitions);
            	writtenElements.add(dataElement);
        	}
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
    

    
    private void associateWrittenDataObjects(Activity activity, Set<DataElementWrapper<?, ?>> readDataElements, Set<DataElementWrapper<?, ?>> writtenDataElements, Collection<Transition> transitions) {
		SubpageElement subPage = subpages.get(activity);
		Set<Set<DataObjectWrapper>> associations = new HashSet<>();
		Set<DataObjectWrapper> readDataObjects = readDataElements.stream().filter(DataElementWrapper::isDataObjectWrapper).map(DataObjectWrapper.class::cast).collect(Collectors.toSet());
		Set<DataObjectWrapper> writtenDataObjects = writtenDataElements.stream().filter(DataElementWrapper::isDataObjectWrapper).map(DataObjectWrapper.class::cast).collect(Collectors.toSet());
		
		for(DataObjectWrapper writtenObject : writtenDataObjects) {
			for(DataObjectWrapper readObject : readDataObjects) {
				if(!writtenObject.equals(readObject) && dataModel.isAssociated(writtenObject.getNormalizedName(), readObject.getNormalizedName())) {
					associations.add(new HashSet<>(Arrays.asList(writtenObject, readObject)));
				}
			}
			for(DataObjectWrapper otherWrittenObject : writtenDataObjects) {
				if(!writtenObject.equals(otherWrittenObject) && dataModel.isAssociated(writtenObject.getNormalizedName(), otherWrittenObject.getNormalizedName())) {
					associations.add(new HashSet<>(Arrays.asList(writtenObject, otherWrittenObject)));
				}
			}
		}
		
		associations.forEach(assoc -> {
			String annotation = assoc.stream().map(DataObjectWrapper::dataElementId).collect(Collectors.toList()).toString();
			for(Transition transition : transitions) {
	    		createArc(subPage.getPage(), transition, subPage.refPlaceFor(associationsPlace), annotation);
			}
		});
		
		if(!associationWriters.contains(activity) && !associations.isEmpty()) {
			createArc(nodeFor(activity), associationsPlace);
			associationWriters.add(activity);
		}
    }
    
    private void checkAssociationsOfReadDataObjects(Activity activity, Set<DataElementWrapper<?, ?>> readDataElements, Collection<Transition> transitions) {
    	Set<DataObjectWrapper> readDataObjects = readDataElements.stream().filter(DataElementWrapper::isDataObjectWrapper).map(DataObjectWrapper.class::cast).collect(Collectors.toSet());
		Set<Set<DataObjectWrapper>> associationsToCheck = new HashSet<>();
		
		for(DataObjectWrapper readObject : readDataObjects) {
			for(DataObjectWrapper otherReadObject : readDataObjects) {
				if(!readObject.equals(otherReadObject) && dataModel.isAssociated(readObject.getNormalizedName(), otherReadObject.getNormalizedName())) {
					associationsToCheck.add(new HashSet<>(Arrays.asList(readObject, otherReadObject)));
				}
			}
		}
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
        	Node source = nodeMap.get(sourceNode);
        	Node target = nodeMap.get(targetNode);
        	//System.out.println(source.getName().asString()+" -> "+target.getName().asString());
        	if(isPlace(source) && isPlace(target)) {
        		Transition transition = builder.addTransition(mainPage, null);
        		builder.addArc(mainPage, source, transition, "caseId");
        		builder.addArc(mainPage, transition, target, "caseId");
        	} else if(isPlace(source) || isPlace(target)) {
        		builder.addArc(mainPage, source, target, "");
        		if(!isPlace(target)) {
        			SubpageElement subPage = subpages.get(targetNode);
        			if(Objects.nonNull(subPage)) {
	        			subPage.getSubpageTransitions().forEach(transition -> {
	            			builder.addArc(subPage.getPage(), subPage.refPlaceFor((Place) source), transition, "caseId");
	        			});
        			}
        		}
        		if(!isPlace(source)) {
        			SubpageElement subPage = subpages.get(sourceNode);
        			if(Objects.nonNull(subPage)) {
            			subPage.getSubpageTransitions().forEach(transition -> {
                			builder.addArc(subPage.getPage(), transition, subPage.refPlaceFor((Place) target), "caseId");
            			});
        			}
        		}
        	} else {
            	Place place = createPlace(null, "CaseID");
            	
            	builder.addArc(mainPage, source, place, "");
       			SubpageElement sourceSubPage = subpages.get(sourceNode);
       			if(Objects.nonNull(sourceSubPage)) {
        			sourceSubPage.getSubpageTransitions().forEach(transition -> {
            			builder.addArc(sourceSubPage.getPage(), transition, sourceSubPage.refPlaceFor(place), "caseId");
        			});
       			}

            	builder.addArc(mainPage, place, target, "");
    			SubpageElement targetSubPage = subpages.get(targetNode);
    			if(Objects.nonNull(targetSubPage)) {
        			targetSubPage.getSubpageTransitions().forEach(transition -> {
            			builder.addArc(targetSubPage.getPage(), targetSubPage.refPlaceFor(place), transition, "caseId");
        			});
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
    }
    
    private <T extends ItemAwareElement> DataElementWrapper<?,?> wrapperFor(T element) {
    	if(element instanceof DataObject) {
    		return wrapperFor((DataObject)element);
    	} else if(element instanceof DataStore) {
    		return wrapperFor((DataStore) element);
    	} else if(element instanceof DataObjectReference) {
    		return wrapperFor((DataObjectReference)element);
    	} else if(element instanceof DataStoreReference) {
    		return wrapperFor((DataStoreReference)element);
    	} else {
    		throw new RuntimeException(element.getClass().getSimpleName());
    	}
    }
    
    private DataObjectWrapper wrapperFor(DataObject object) {
    	return dataObjectWrappers.stream().filter(any -> any.isForElement(object)).findAny().get();
    }
    
    private DataObjectWrapper wrapperFor(DataObjectReference reference) {
    	return dataObjectWrappers.stream().filter(any -> any.isForReference(reference)).findAny().get();
    }
    
    
    private DataStoreWrapper wrapperFor(DataStore object) {
    	return dataStoreWrappers.stream().filter(any -> any.isForElement(object)).findAny().get();
    }
    
    private DataStoreWrapper wrapperFor(DataStoreReference reference) {
    	return dataStoreWrappers.stream().filter(any -> any.isForReference(reference)).findAny().get();
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
    
    public RefPlace createFusionPlace(Page page, String name, String type, String initialMarking, String fusionGroupName) {
    	return builder.addFusionPlace(page, name, type, initialMarking, fusionGroupName);
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
	
	public Node nodeFor(BaseElement element) {
		return nodeMap.get(element);
	}
	
    //========Static========
	public static String elementName(FlowElement element) {
    	String name = element.getName();
    	if(name == null || name.equals(""))name = element.getId();
    	return name;
	}
	
    public static String normalizeElementName(String name) {
    	return name.replace('\n', ' ').trim();
    }
    
    public static <T extends ModelElementInstance> List<String> getReferenceIds(DataAssociation association, Class<T> referenceClass) {
		return association.getChildElementsByType(referenceClass).stream()
				.map(each -> each.getTextContent())
				.collect(Collectors.toList());
    }
    
    public static Stream<String> dataObjectStateToNetColors(String state) {
    	return Arrays.stream(state.replaceAll("\\[", "").replaceAll("\\]", "").split("\\|"))
    			.map(String::trim)
    			.map(each -> each.replaceAll("\\s","_"))
    			.map(String::toUpperCase);
    }
    
	public static <T> List<List<T>> allCombinationsOf(Collection<List<T>> sets) {
        int numberOfCombinations = sets.stream()
                .mapToInt(Collection::size)
                .reduce(1, (a,b) -> a*b);
        
        List<List<T>> combinations = new ArrayList<>(numberOfCombinations);
        for(int i = 0; i < numberOfCombinations; i++) {
            int j = 1;
            List<T> combination = new ArrayList<>();
            for(List<T> objectVariants : sets) {
            	combination.add(objectVariants.get((i/j)%objectVariants.size()));
                j *= objectVariants.size();
            }
            combinations.add(combination);
        }
        return combinations;
	}

}
