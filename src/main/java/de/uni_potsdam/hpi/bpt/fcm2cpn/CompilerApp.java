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

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.assumeNameIsNormalized;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.ModelFactory;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNList;
import org.cpntools.accesscpn.model.cpntypes.CPNProduct;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModelParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycleParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataElementWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataStoreWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions.TerminationCondition;
import de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions.TerminationConditionCompiler;
import de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions.TerminationConditionParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.AbstractPageScope;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;


public class CompilerApp implements AbstractPageScope {

    public final static String licenseInfo = "fCM2CPN translator  Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam, Germany\n" +
            "This program comes with ABSOLUTELY NO WARRANTY.\n" +
            "This is free software, and you are welcome to redistribute it under certain conditions.\n";

    private static final FileNameExtensionFilter bpmnFileFilter = new FileNameExtensionFilter("BPMN Process Model", "bpmn");
    private static final FileNameExtensionFilter umlFileFilter = new FileNameExtensionFilter("UML Data Model Class Diagram", "uml");
    private static final FileNameExtensionFilter jsonFileFilter = new FileNameExtensionFilter("JSON Termination Condition", "json");
    private ObjectLifeCycle[] olcs;

    /** The bpmn model to be parsed*/
	private BpmnModelInstance bpmn;
    /** Parsed data model of the bpmn model*/
	private DataModel dataModel;
	/** Termination condition */
	private Optional<TerminationCondition> terminationCondition;
	
	/** Helper for constructing the resulting net*/
	private final BuildCPNUtil builder;
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
	
	/** Global place that registers all tokens*/
	private Place registryPlace;
	
	/** Global place active cases*/
	public static final String ACTIVE_CASES_PLACE_NAME = "active_cases";
	private Place activeCasesPlace;
	
	/** Global place for terminated cases*/
	public static final String TERMINATED_CASES_PLACE_NAME = "terminated_cases";
	private Place terminatedCasesPlace;
	
	/** Steps that run after (most of) the net is created; used e.g. in {@link #translateBoundaryEvents()} to access all control flow places of an interrupted activity*/
	private List<Runnable> deferred;
	

	
	/** Wrapper for data objects, see {@link DataObjectWrapper}*/
	private Collection<DataObjectWrapper> dataObjectWrappers;

	/** Wrapper for data stores, see {@link DataStoreWrapper}*/
	private Collection<DataStoreWrapper> dataStoreWrappers;

    public static void main(final String[] _args) throws Exception {
        System.out.println(licenseInfo);
        final LinkedList<String> args = new LinkedList<>(Arrays.asList(_args));
        Supplier<Boolean> nextArgIsFile = () -> !args.isEmpty() && !args.get(0).startsWith("-");
        
        File bpmnFile;
        Optional<File> dataModelFile = Optional.empty();
        Optional<File> terminationConditionFile = Optional.empty();
        
        if(nextArgIsFile.get()) {
        	bpmnFile = new File(args.get(0));
        	args.remove(0);
        } else {
        	bpmnFile = getFile(bpmnFileFilter);
        }        
        if (null == bpmnFile) {
            System.exit(0);
        }
        
        while(!args.isEmpty()) {
        	String nextArg = args.removeFirst();
        	assert nextArg.startsWith("-");
        	if(nextArg.equals("-d")) {
                if(nextArgIsFile.get()) {
                	dataModelFile = Optional.of(new File(args.removeFirst()));
                } else {
                	dataModelFile = Optional.ofNullable(getFile(umlFileFilter));
                }  
        	} else if(nextArg.equals("-t")) {
                if(nextArgIsFile.get()) {
                	terminationConditionFile = Optional.of(new File(args.removeFirst()));
                } else {
                	dataModelFile = Optional.ofNullable(getFile(jsonFileFilter));
                }  
        	}
        }

        BpmnModelInstance bpmn = loadBPMNFile(bpmnFile);
        Optional<DataModel> dataModel = dataModelFile.map(DataModelParser::parse);
        Optional<TerminationCondition> terminationCondition = terminationConditionFile.map(TerminationConditionParser::parse);
        PetriNet petriNet = translateBPMN2CPN(bpmn, dataModel, terminationCondition);
		ModelPrinter.printModel(petriNet);
        System.out.print("Writing CPN file... ");
        DOMGenerator.export(petriNet, "./"+bpmnFile.getName().replaceAll("\\.bpmn", "")+".cpn");
        System.out.println("DONE");
    }
    
    public static PetriNet translateBPMN2CPN(BpmnModelInstance bpmn, Optional<DataModel> dataModel, Optional<TerminationCondition> terminationCondition) {
    	return new CompilerApp(bpmn, dataModel, terminationCondition).translateBPMN2CPN();
    }

    private static File getFile(FileNameExtensionFilter filter) {
        JFileChooser chooser = new JFileChooser("./");
        chooser.setFileFilter(filter);
        int returnVal = chooser.showOpenDialog(null);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return  null;
    }

    private CompilerApp(BpmnModelInstance bpmn, Optional<DataModel> dataModel, Optional<TerminationCondition> terminationCondition) {
    	this.bpmn = bpmn;
        this.builder = new BuildCPNUtil();
        this.dataModel = dataModel.orElse(DataModel.none());
        this.terminationCondition = terminationCondition;
        this.subpages = new HashMap<>();
        this.nodeMap = new HashMap<>();
        this.deferred = new ArrayList<>();
	}
    
    private static BpmnModelInstance loadBPMNFile(File bpmnFile) {
        System.out.print("Load and parse BPMN file... ");
        BpmnModelInstance bpmn = Bpmn.readModelFromFile(bpmnFile);
        System.out.println("DONE");
        return bpmn;
    }

    private PetriNet translateBPMN2CPN() {
    	preprocessBpmnModel();
    	initializeCPNModel();
    	System.out.print("Translating BPMN... ");
        translateData();
        translateActivities();
        translateEvents();
        translateGateways();
        translateControlFlow();
        translateTerminationCondition();
        runDeferredCalls();
        layout();
        System.out.println("DONE");
        return petriNet;
    }

	private void preprocessBpmnModel() {
		BpmnPreprocessor.process(bpmn);
        olcs = ObjectLifeCycleParser.getOLCs(dataModel, bpmn);
	}

	private void initializeCPNModel() {
        System.out.print("Initalizing CPN model... ");
        petriNet = builder.createPetriNet();
        petriNet.setName(ModelFactory.INSTANCE.createName());
        petriNet.getName().setText("Compiled BPMN Model");
        mainPage = createPage("Main_Page");
        initializeDefaultColorSets();
        initializeDefaultVariables();
        initializeUtilityFunctions();
        
        createActiveCasesPlace();
        createTerminatedCasesPlace();
        
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
        	.map(Utils::elementName)
        	.distinct()
            .forEach(typeEnum::addValue);
        if(dataTypes.isEmpty()) typeEnum.addValue("NO_TYPES");
        builder.declareColorSet(petriNet, "TYPE", typeEnum);
        
        // ID 
        CPNProduct id = CpntypesFactory.INSTANCE.createCPNProduct();
        id.addSort("TYPE");
        id.addSort("INT");
        builder.declareColorSet(petriNet, "ID", id);
        
        // DataObject & ListOfDataObjects
        CPNRecord dataObject = CpntypesFactory.INSTANCE.createCPNRecord();
        dataObject.addValue("id", "ID");
        dataObject.addValue(caseId(), "STRING");
        builder.declareColorSet(petriNet, "DATA_OBJECT", dataObject);

        CPNList listOfDataObject = CpntypesFactory.INSTANCE.createCPNList();
        listOfDataObject.setSort("DATA_OBJECT");
        builder.declareColorSet(petriNet, "LIST_OF_DATA_OBJECT", listOfDataObject);
        
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
    	createVariable("registry", "LIST_OF_DATA_OBJECT");
    }
    
    private void initializeUtilityFunctions() {
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
        
        builder.declareMLFunction(petriNet, 
        		"fun unpack [(a1, a2), (b1, b2)] class = \n" + 
        		"if a1 = class then (a1, a2)\n" + 
        		"else (b1, b2);");
        
        builder.declareMLFunction(petriNet, 
        		"fun enforceLowerBound id class assoc bound =\n" + 
        		"(length (listAssocs id class assoc) >= bound);");

        builder.declareMLFunction(petriNet, 
        		"fun enforceLowerBoundForEach list class assoc bound =\n" +
				"(List.all (fn(oId) => enforceLowerBound oId class assoc bound) (List.map (fn({id = id, caseId = _}) => id) list));");
        
        builder.declareMLFunction(petriNet, 
        		"fun enforceUpperBound id class assoc bound =\n" + 
        		"(length (listAssocs id class assoc) < bound);");
        
        builder.declareMLFunction(petriNet, 
        		"fun associateWithList singleObject listObject identifier assoc =\n" + 
        		"(map (fn(el) => [singleObject, unpack el listObject]) (listAssocs identifier listObject assoc));");
        //TODO rename to associateWithCollection?
        
        builder.declareMLFunction(petriNet,
        		"fun allAssociated sourceId collectionClass caseId assoc =\n" + 
        		"map (fn(el) => {id = unpack el collectionClass , caseId = caseId}) (listAssocs sourceId collectionClass assoc);");
	}


    
    private void translateData() {
        translateDataObjects();        
        translateDataStores();
        createAssociationPlace();
        createRegistryPlace();
    }
    
    
    private void translateDataObjects() {
        Map<String, DataObjectWrapper> dataObjectsNamesToWrappers = new HashMap<>();

    	Collection<DataObject> dataObjects = bpmn.getModelElementsByType(DataObject.class);
        dataObjects.forEach(each -> dataObjectsNamesToWrappers
        		.computeIfAbsent(elementName(each), normalizedName -> new DataObjectWrapper(this, normalizedName))
        		.addMappedElement(each));

        Collection<DataObjectReference> dataObjectRefs = bpmn.getModelElementsByType(DataObjectReference.class);
        dataObjectRefs.forEach(each -> dataObjectsNamesToWrappers
        		.get(elementName(each.getDataObject()))
        		.addMappedReference(each));
        
        dataObjectWrappers = dataObjectsNamesToWrappers.values();
    }
    
    private void translateDataStores() {
        Map<String, DataStoreWrapper> dataStoreNamesToWrappers = new HashMap<>();
        
        Collection<DataStore> dataStores = bpmn.getModelElementsByType(DataStore.class);
        dataStores.forEach(each -> dataStoreNamesToWrappers
        		.computeIfAbsent(elementName(each), normalizedName -> new DataStoreWrapper(this, normalizedName))
        		.addMappedElement(each));
        Collection<DataStoreReference> dataStoreRefs = bpmn.getModelElementsByType(DataStoreReference.class);
        dataStoreRefs.forEach(each -> dataStoreNamesToWrappers
        		.get(elementName(each.getDataStore()))
        		.addMappedReference(each));
        
        dataStoreWrappers = dataStoreNamesToWrappers.values();
    }
    
    private void createAssociationPlace() {
    	associationsPlace = createPlace("associations", "LIST_OF_ASSOCIATION", "[]");
    }
    
    private void createRegistryPlace() {
    	registryPlace = createPlace("objects", "LIST_OF_DATA_OBJECT", "[]");
    }
    
    
    private void createActiveCasesPlace() {
    	activeCasesPlace = createPlace(ACTIVE_CASES_PLACE_NAME, "CaseID");
    }
    
    private void createTerminatedCasesPlace() {
    	terminatedCasesPlace = createPlace(TERMINATED_CASES_PLACE_NAME, "CaseID");
    }
    
    private void translateActivities() {
        Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
        activities.forEach(activity -> {
        	new ActivityCompiler(this, activity).compile();
        });
    }
    
    private void translateEvents() {
    	translateStartEvents();
    	translateEndEvents();
    	translateBoundaryEvents();
    }

	private void translateStartEvents() {
        Collection<StartEvent> events = bpmn.getModelElementsByType(StartEvent.class);
        events.forEach(startEvent -> {
        	new StartEventCompiler(this, startEvent).compile();
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
            SubpageElement subPage = createSubpage(each);
        	Transition subpageTransition = subPage.createTransition(elementName(each));

		    //Must be called after control flow places are created, to know which will be there and which to consume
        	defer(() -> {
    			Node attachedNode = nodeFor(each.getAttachedTo());
    			assert !isPlace(attachedNode);
    			attachedNode.getTargetArc().stream()
    				.map(arc -> arc.getPlaceNode())
    				.filter(place -> place.getSort().getText().equals("CaseID"))
    				.forEach(place -> {
    					subPage.createArc(subPage.refPlaceFor((Place) place), subpageTransition, caseId());
    					createArc(place, subPage.getMainPageTransition(), "");
    				});
        	});
        });
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
        	SubpageElement subPage = createSubpage(each);
	    	subPage.createTransition(elementName(each));
        });
    }
    
    private void translateControlFlow() {
        Collection<SequenceFlow> sequenceFlows = bpmn.getModelElementsByType(SequenceFlow.class);
        sequenceFlows.forEach(eachFlow -> {
        	FlowNode sourceNode = eachFlow.getSource();
        	FlowNode targetNode = eachFlow.getTarget();
        	Node source = nodeFor(sourceNode);
        	Node target = nodeFor(targetNode);
        	//System.out.println(source.getName().asString()+" -> "+target.getName().asString());
        	if(isPlace(source) && isPlace(target)) {
        		Transition transition = createTransition(elementName(eachFlow));
        		createArc(source, transition, caseId());
        		createArc(transition, target, caseId());
        		
        	} else if(isPlace(source) || isPlace(target)) {
        		createArc(source, target, "");
        		if(subpages.containsKey(targetNode)) {
        			subpages.get(targetNode).createArcsFrom((Place) source, caseId());
        		}
        		if(subpages.containsKey(sourceNode)) {
        			subpages.get(sourceNode).createArcsTo((Place) target, caseId());
        		}
        		
        	} else {
            	Place place = createPlace(elementName(eachFlow), "CaseID");
            	
            	createArc(source, place, "");
       			if(subpages.containsKey(sourceNode)) {
           			subpages.get(sourceNode).createArcsTo(place, caseId());
       			}

       			createArc(place, target, "");
    			if(subpages.containsKey(targetNode)) {
        			subpages.get(targetNode).createArcsFrom(place, caseId());
    			}
        	}
        });
    }
    
    private static boolean isPlace(Node node) {
    	return node instanceof Place;
    }
    
    private void translateTerminationCondition() {
    	terminationCondition.ifPresent(tc -> new TerminationConditionCompiler(this, tc).compile());
    }
    
    private void defer(Runnable r) {
    	deferred.add(r);
    }
    
    private void runDeferredCalls() {
    	deferred.forEach(Runnable::run);
    }
    
    private void layout() {
    	//TODO layouting is TBD
    }
    
    public DataElementWrapper<?,?> wrapperFor(StatefulDataAssociation<?, ?> assoc) {
    	return Stream.concat(dataObjectWrappers.stream(), dataStoreWrappers.stream())
    			.filter(any -> any.isForReference(assoc.getDataElement())).findAny().get();
    }  
    
    public ObjectLifeCycle olcFor(DataObjectWrapper dataObject) {
    	return Arrays.stream(olcs)
    			.filter(olc -> olc.getClassName().equals(dataObject.getNormalizedName()))
    			.findAny()
    			.get();
    }
    
    public DataObjectWrapper getDataObjectCollectionIdentifier(DataObjectWrapper object, Set<StatefulDataAssociation<DataInputAssociation, ?>> availableInputs) {
    	Set<DataObjectWrapper> potentialIdentifiers = availableInputs.stream()
			.filter(StatefulDataAssociation::isDataObjectReference)
    		.map(this::wrapperFor)
    		.map(DataObjectWrapper.class::cast)
			.filter(isValidCollectionIdentifierFor(object))
			.collect(Collectors.toSet());
		assert potentialIdentifiers.size() == 1;
		return potentialIdentifiers.stream().findAny().get();
    } 
    
    private Predicate<DataObjectWrapper> isValidCollectionIdentifierFor(DataObjectWrapper objectToIdentify) {
		return potentialIdentifier -> dataModel.getAssociation(potentialIdentifier.getNormalizedName(), objectToIdentify.getNormalizedName())
            .filter(identifyingAssoc -> identifyingAssoc.getEnd(potentialIdentifier.getNormalizedName()).getUpperBound() <= 1)
            .isPresent();    
    }
    
    
    public SubpageElement createSubpage(FlowElement element) {
    	String name = elementName(element);
        SubpageElement subpage = createSubpage(name);
        subpages.put(element, subpage);
        nodeMap.put(element, subpage.getMainPageTransition());
        return subpage;
    }
    
    public SubpageElement createSubpage(String name) {
    	assumeNameIsNormalized(name);
    	Page activityPage = createPage(name);
        Instance mainPageTransition = createSubpageTransition(name, activityPage);
        return new SubpageElement(this, activityPage, mainPageTransition);
    }
    
    //========Accessors======
	
	public BpmnModelInstance getBpmn() {
		return bpmn;
	}
	
	public DataModel getDataModel() {
		return dataModel;
	}

	public Place getRegistryPlace() {
		return registryPlace;
	}

	public Place getAssociationsPlace() {
		return associationsPlace;
	}
	
	public Place getActiveCasesPlace() {
		return activeCasesPlace;
	}

	public Place getTerminatedCasesPlace() {
		return terminatedCasesPlace;
	}

	public Node nodeFor(BaseElement element) {
		return nodeMap.get(element);
	}
	
	public String caseId() {
		return "caseId";
	}

	public Collection<DataObjectWrapper> getDataObjects() {
		return dataObjectWrappers;
	}
	
	public DataObjectWrapper dataObjectByName(String dataObjectName) throws NoSuchElementException {
		assumeNameIsNormalized(dataObjectName);
		return getDataObjects().stream().filter(dataObject -> dataObject.getNormalizedName().equals(dataObjectName)).findAny().get();
	}
	
	public Collection<DataStoreWrapper> getDataStores() {
		return dataStoreWrappers;
	}

	@Override
	public BuildCPNUtil builder() {
		return builder;
	}

	@Override
	public PetriNet petriNet() {
		return petriNet;
	}

	@Override
	public Page getPage() {
		return mainPage;
	}

}
