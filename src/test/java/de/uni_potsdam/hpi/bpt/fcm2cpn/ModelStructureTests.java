package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.engine.highlevel.HighLevelSimulator;
import org.cpntools.accesscpn.engine.highlevel.checker.Checker;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModelParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycleParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelConsumerTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIdIOSet;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

//TODO Model structure is quite generic, the class hierarchy naming should show that compiled cpn model structure is meant
public abstract class ModelStructureTests extends ModelConsumerTest {

	public PetriNet petrinet;
	
	public DataModel dataModel;
	
	public ObjectLifeCycle[] olcs;
	
	public List<FlowElement> readingElements(ItemAwareElement dataElementReference) {
		return bpmn.getModelElementsByType(DataInputAssociation.class).stream()
				.filter(assoc -> assoc.getSources().contains(dataElementReference))
				.map(DataInputAssociation::getParentElement)
				.map(FlowElement.class::cast)
				.collect(Collectors.toList());
	}
	
	public List<FlowElement> writingElements(ItemAwareElement dataElementReference) {
		return bpmn.getModelElementsByType(DataOutputAssociation.class).stream()
				.filter(assoc -> assoc.getTarget().equals(dataElementReference))
				.map(DataOutputAssociation::getParentElement)
				.map(FlowElement.class::cast)
				.collect(Collectors.toList());
	}
	
	public List<String[]> dataObjectAssociations() {
		List<String> dataObjects = bpmn.getModelElementsByType(DataObject.class).stream()
				.map(Utils::elementName)
				.distinct()
				.collect(Collectors.toList());
		List<String[]> associated = new ArrayList<>();
		for(int i = 0; i < dataObjects.size(); i++) {
			String first = dataObjects.get(i);
			for(int j = 0; j < dataObjects.size(); j++) {
				if(i == j)continue;
				String second = dataObjects.get(j);
				if(dataModel.isAssociated(first, second)) associated.add(new String[] {first, second});
			}
		}
		return associated;
	}
	
	public static Stream<DataObjectReference> readDataObjectRefs(Activity activity) {
		return activity.getDataInputAssociations().stream()
				.flatMap(assoc -> assoc.getSources().stream())
				.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
	}
	
	public static Stream<DataObjectReference> writtenDataObjectRefs(Activity activity) {
		return activity.getDataOutputAssociations().stream()
				.map(assoc -> assoc.getTarget())
				.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
	}
	
	public static Stream<DataObjectReference> writtenDataObjectRefs(StartEvent startEvent) {
		return startEvent.getDataOutputAssociations().stream()
				.map(assoc -> assoc.getTarget())
				.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
	}
	
	
	public static Map<String, List<String>> dataObjectToStateMap(Stream<DataObjectReference> dataObjectReferences) {
		return dataObjectReferences
			.filter(each -> Objects.nonNull(each.getDataState()))
			.collect(Collectors.groupingBy(
					each -> elementName(each.getDataObject()),
					Collectors.flatMapping(each -> Utils.dataObjectStateToNetColors(each.getDataState().getName()), Collectors.toList())));
	}
	
	public static Set<Pair<List<DataInputAssociation>, List<DataOutputAssociation>>> statelessAssociationCombinations(Activity activity) {
		Set<Pair<List<DataInputAssociation>, List<DataOutputAssociation>>> combinations = new HashSet<>();
		activity.getIoSpecification().getInputSets().forEach(inputSet -> {
			inputSet.getOutputSets().forEach(outputSet -> {
				combinations.add(new Pair<>(
						inputSet.getDataInputs().stream().map(Utils::getAssociation).collect(Collectors.toList()), 
						outputSet.getDataOutputRefs().stream().map(Utils::getAssociation).collect(Collectors.toList()))
				);
			});	
		});
		return combinations;		
	}
	

	@SuppressWarnings("unchecked")
	public static Set<DataObjectIdIOSet> ioAssociationCombinations(Activity activity) {
		return statelessAssociationCombinations(activity).stream().flatMap(ioAssociationCombination -> {
			Map<Pair<String, Boolean>, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> inputStates = ioAssociationCombination.first.stream()
					.flatMap(Utils::splitDataAssociationByState)
					.filter(StatefulDataAssociation::isDataObjectReference)
					.map(assoc -> (StatefulDataAssociation<DataInputAssociation, DataObjectReference>) assoc)
					.collect(Collectors.groupingBy(StatefulDataAssociation::dataElementNameAndCollection));
			Map<Pair<String, Boolean>, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> outputStates = ioAssociationCombination.second.stream()
					.flatMap(Utils::splitDataAssociationByState)
					.filter(StatefulDataAssociation::isDataObjectReference)
					.map(assoc -> (StatefulDataAssociation<DataOutputAssociation, DataObjectReference>) assoc)
					.collect(Collectors.groupingBy(StatefulDataAssociation::dataElementNameAndCollection));

			List<List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> possibleInputForms = Utils.allCombinationsOf(inputStates.values());
			List<List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> possibleOutputForms = Utils.allCombinationsOf(outputStates.values());
			List<DataObjectIdIOSet> ioForms = new ArrayList<>();
			for(var inputForm: possibleInputForms) {
				for(var outputForm: possibleOutputForms) {
					ioForms.add(new DataObjectIdIOSet(inputForm, outputForm));
				}
			}
			return ioForms.stream();
		})
		.collect(Collectors.toSet());
	}
	
	public static Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> ioAssociationsToStateMaps(DataObjectIdIOSet ioAssociations) {
		Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> ioConfiguration = new Pair<>(
				ioAssociations.first.stream()
					.collect(Collectors.toMap(StatefulDataAssociation::dataElementNameAndCollection, StatefulDataAssociation::getStateName)),
				ioAssociations.second.stream()
					.collect(Collectors.toMap(StatefulDataAssociation::dataElementNameAndCollection, StatefulDataAssociation::getStateName)));	

		//Expect read objects that are not explicitly written to be written back in the same state as they are read
		for(StatefulDataAssociation<DataInputAssociation, DataObjectReference> inputAssoc : ioAssociations.first) {
			if(ioAssociations.second.stream().noneMatch(inputAssoc::equalsDataElementAndCollection))
				ioConfiguration.second.put(inputAssoc.dataElementNameAndCollection(), inputAssoc.getStateName());
		}
		
		return ioConfiguration;
	}
	
	public static Set<Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>> expectedIOCombinations(Activity activity) {
		return ioAssociationCombinations(activity).stream()
				.map(ModelStructureTests::ioAssociationsToStateMaps).collect(Collectors.toSet());
	}
	
	public static Stream<Pair<Pair<String, Boolean>, String>> expectedCreatedObjects(Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> ioCombination) {
		return ioCombination.second.entrySet().stream()
			.filter(idAndState -> !ioCombination.first.containsKey(idAndState.getKey()))
			.map(entry -> new Pair<>(entry.getKey(), entry.getValue()));
	}
	
	public Set<Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>> ioCombinationsInNet(Activity activity) {
		Set<Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>> ioCombinations = new HashSet<>();
		transitionsFor(activity).forEach(transition -> 
			ioCombinations.add(ioCombinationOfTransition(transition)));
		return ioCombinations;
	}
	
	//TODO duplicate to activityTransitionsForTransput ?
	public Optional<Transition> transitionForIoCombination(Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> ioCombination, Activity activity) {
		return transitionsFor(activity)
			.filter(transition -> ioCombinationOfTransition(transition).equals(ioCombination))
			.findAny();
	}
	
	
	public boolean isValidCollectionIdentifier(String identifier, String identified, DataObjectIdIOSet ioSet) {
		return ioSet.reads(identifier) 
				&& dataModel.isAssociated(identifier, identified) 
				&& dataModel.getAssociation(identifier, identified).get().getEnd(identifier).getUpperBound() == 1;
	}
	
	public boolean isDirectLowerBoundGuard(String guard, String first, String second, int lowerBound) {
		return removeComments(guard).equals("(enforceLowerBound "+first+"Id "+second+" assoc "+lowerBound+")");
	}
	
	public boolean isLowerBoundGuardViaCollectionIdentifier(String guard, String first, String second, int lowerBound, DataObjectIdIOSet ioSet) {
		guard = removeComments(guard);
		String beforeIdentifier = "(enforceLowerBound ";
		String afterIdentifier = "Id "+second+" assoc "+lowerBound+")";
		String identifier = guard.replace(beforeIdentifier, "").replace(afterIdentifier, "");
		return guard.startsWith(beforeIdentifier) && guard.endsWith(afterIdentifier) && isValidCollectionIdentifier(identifier, second, ioSet);
	}

	//TODO
	public Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> ioCombinationOfTransition(Transition transition) {
		Map<Pair<String, Boolean>, String> inputs = new HashMap<>();
		Map<Pair<String, Boolean>, String> outputs = new HashMap<>();
		transition.getTargetArc().stream()
			.map(inputArc -> inputArc.getHlinscription().asString())
			.forEach(inscription -> parseCreatedTokenIdAndState(inscription, transition).ifPresent(idAndState -> inputs.put(idAndState.first, idAndState.second)));

		transition.getSourceArc().stream()
			.map(outputArc -> outputArc.getHlinscription().asString())
			.forEach(inscription -> parseCreatedTokenIdAndState(inscription, transition).ifPresent(idAndState -> outputs.put(idAndState.first, idAndState.second)));
		return new Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>>(inputs, outputs);
	}
	
	//TODO
	public static Optional<Pair<Pair<String, Boolean>, String>> parseCreatedTokenIdAndState(String inscription, Transition transition) {
		String dataId = null;
		String state = null;
		boolean isCollection = false;

		int startIndex = inscription.indexOf("{");
		int endIndex = inscription.indexOf("}");
		if(startIndex != -1 && endIndex != -1) {
			String potentialCreation = inscription.substring(startIndex+1, endIndex);
			List<String[]> statements = Arrays.stream(potentialCreation.split(","))
					.map(String::trim)
					.map(statement -> statement.split("="))
					.filter(statement -> statement.length == 2)
					.collect(Collectors.toList());
				for(String[] statement : statements) {
					if(statement[0].trim().equals("id")) dataId = statement[1].trim().replaceAll("Id$", "");
					if(statement[0].trim().equals("state")) state = statement[1].trim();
				}
		} else if(inscription.contains("_list")) {
			isCollection = true;
			String[] tokens = inscription.split(" ");
			if(tokens.length == 3 && tokens[0].equals("mapState") && tokens[1].endsWith("_list")) {
				dataId = tokens[1].replaceAll("_list$", "");
				state = tokens[2];
			} else if(tokens.length == 1 && tokens[0].endsWith("_list")) {
				dataId = tokens[0].replaceAll("_list$", "");
				state = guardsOf(transition)
					.filter(guard -> guard.startsWith(tokens[0]+" = "))
					.map(guard -> guard.split("state = ")[1].split("}")[0])
					.findAny().orElse(null);
			}
		}
		if(dataId != null) return Optional.of(new Pair<>(new Pair<>(dataId, isCollection), state));
		else return Optional.empty();
	}
	
	public static <Key, T> List<Map<Key, T>> indexedCombinationsOf(Map<Key, List<T>> groups) {
		//Get defined order into collection
		List<Key> keys = new ArrayList<>(groups.keySet());
		List<List<T>> combinations = Utils.allCombinationsOf(keys.stream().map(groups::get).collect(Collectors.toList()));
		
		//Zip keys with each combination
		return combinations.stream().map(combination -> {
			HashMap<Key, T> map = new HashMap<>();
			for(int i = 0; i < keys.size(); i++) {
				map.put(keys.get(i), combination.get(i));
			}
			return map;
		}).collect(Collectors.toList());
	}
	
	public static Predicate<Arc> writesAssociation(String first, String second) {
		return arc -> {
			String inscription = arc.getHlinscription().getText();
			String list = inscription.replaceFirst("assoc", "").trim();
			Stream<?> matchingAssocWrites = Arrays.stream(list.split("\\^\\^"))
					.map(ModelStructureTests::toSet)
					.flatMap(Set::stream)
					.filter(assoc -> isAssocInscriptionFor(assoc, first, second));
			Stream<?> matchingListAssocWrites = Arrays.stream(list.split("\\^\\^"))
					.filter(expr -> expr.startsWith("(associateWithList"))
					.map(expr -> expr.substring(1, expr.length()-1).split(" "))
					.map(tokens -> Stream.of(tokens[1], tokens[2]+"Id").sorted().toArray())
					.filter(pair -> Arrays.equals(pair, Stream.of(second, first).sorted().toArray()));
			
			return inscription.startsWith("assoc") && matchingAssocWrites.count() + matchingListAssocWrites.count() == 1;
		};
	}
	
	public static Stream<String> guardsOf(Transition transition) {
		String guard = transition.getCondition().getText().replaceFirst("^\\[", "").replaceFirst("]$", "");
		return Arrays.stream(guard.split(",\n"))
				.map(String::trim);
	}
	
	public static String removeComments(String guard) {
		return guard.replaceAll("\\(\\*(.|[\\r\\n])*?\\*\\)", "").trim();
	}
	
	public static boolean hasGuardForAssociation(Transition transition, String first, String second) {
		return guardsOf(transition).anyMatch(singleCondition -> {
			String list = singleCondition.replace("contains assoc ", "").trim();
			return (singleCondition.contains("contains assoc") && toSet(list).stream().filter(assoc -> isAssocInscriptionFor(assoc, first+"Id", second+"Id")).count() == 1)
					|| singleCondition.matches("\\(enforceLowerBound "+first+"Id "+second+" assoc (.*)\\)")
					|| singleCondition.matches("\\(enforceLowerBound "+second+"Id "+first+" assoc (.*)\\)")
					//TODO when there is a distinct function for getting all associated objects, this can serve as more precise condition
					|| singleCondition.endsWith("(listAssocs "+first+"Id "+second+" assoc))") || singleCondition.endsWith("(listAssocs "+second+"Id "+first+" assoc))");
		});
	}
	
	public static boolean isAssocInscriptionFor(String inscription, String... elements) {
		if(!(inscription.startsWith("[") && inscription.endsWith("]"))) return false;
		Set<String> listElements = toSet(inscription);
		return listElements.equals(new HashSet<>(Arrays.asList(elements)));
	}
	
	public static Set<String> toSet(String inscription) {
		if(!(inscription.startsWith("[") && inscription.endsWith("]"))) return Collections.emptySet();
		String body = inscription.substring(1, inscription.length() - 1);
		Set<String> set = new HashSet<>();
		String currentElement = "";
		int currentDepth = 0;
		for(char c : body.toCharArray()) {
			if(c == '[') currentDepth++;
			else if(c == ']') currentDepth--;
			
			if(c == ',' && currentDepth == 0) {
				set.add(currentElement.trim());
				currentElement = "";
			} else currentElement += c;
		}
		set.add(currentElement.trim());
		return set;
	}
	
	public <T extends ModelElementInstance> void forEach(Class<T> elementClass, Consumer<? super T> testBody) {
		bpmn.getModelElementsByType(elementClass).forEach(testBody);
	}
	
	public void checkNet() throws Exception {
        try {
        	HighLevelSimulator simu = HighLevelSimulator.getHighLevelSimulator();
        	
        	Checker checker = new Checker(petrinet, null, simu);
        	checker.checkEntireModel();
		} catch(NoSuchElementException e) {
			// From Packet:170, weird bug, but catching this error seems to work
		}
	}
	
	public Page mainPage() {
		return petrinet.getPage().get(0);
	}
	
	public Stream<Instance> instancesNamed(String name) {
		return StreamSupport.stream(mainPage().instance().spliterator(), false).filter(instance -> instance.getName().asString().equals(name));
	}
	
	public Stream<Page> pagesNamed(String name) {
		return petrinet.getPage().stream().filter(page -> page.getName().asString().equals(name));
	}
	
	public Page pageNamed(String name) {
		return pagesNamed(name).findAny().get();
	}
	
	public Stream<Place> placesNamed(String name) {
		return StreamSupport.stream(mainPage().place().spliterator(), false)
				.filter(place -> Objects.toString(place.getName().asString()).equals(name));
	}

	public Stream<Place> dataObjectPlaces(String dataObjectName, String state) {
		return placesNamed(Utils.dataPlaceName(dataObjectName, state))
				.filter(place -> place.getSort().getText().equals("DATA_OBJECT") );
	}
	
	public Stream<Place> dataObjectPlacesForAllStates(String dataObjectName) {
		return Utils.dataObjectStates(dataObjectName, bpmn).stream().flatMap(state -> dataObjectPlaces(dataObjectName, state));
	}
	
	public Stream<Place> dataStorePlacesNamed(String name) {
		return placesNamed(name)
				.filter(place -> place.getSort().getText().equals("DATA_STORE") );
	}
	

	public Stream<Place> controlFlowPlacesBetween(String elementA, String elementB) {	
		Node nodeA = nodeFor(elementA);
		Node nodeB = nodeFor(elementB);	
		return StreamSupport.stream(mainPage().place().spliterator(), false)
				.filter(place -> isControlFlowPlace(place) && connects(place, nodeA, nodeB));
	}
	
	/**
	 * Control flow between two bpmn elements is correctly mapped if: <br>
	 * a) there is a control flow place between transitions with the names <br>
	 * XOR<br> 
	 * b) if there is a place for one of them that is connected to a transition for the other one<br>
	 * XOR<br>
	 * c) if both are mapped to places and there is a control flow transition between the two
	 */
	public Stream<Object> controlFlowMappingsBetween(String elementA, String elementB) {
		Node nodeA = nodeFor(elementA);
		Node nodeB = nodeFor(elementB);
		Stream<Arc> controlFlowArcs = mainPage().getArc().stream()
				.filter(arc -> arc.getSource().equals(nodeA) && arc.getTarget().equals(nodeB));
		Stream<Place> controlFlowPlaces = controlFlowPlacesBetween(elementA, elementB);
		Stream<Transition> controlFlowTransitions = StreamSupport.stream(mainPage().transition().spliterator(), false)
				.filter(transition -> connects(transition, nodeA, nodeB));
		
		return Stream.of(controlFlowArcs, controlFlowPlaces, controlFlowTransitions).flatMap(Function.identity());
	}
	
	public Node nodeFor(String element) {
		return allMainPageNodes()
			.filter(node -> element.equals(node.getName().asString()))
			.findAny()
			.get();
	}
	
	public Stream<Node> allMainPageNodes() {
		return allNodes(mainPage());
	}
	
	public Stream<Node> allNodes(Page page) {
		return Stream.of(
				StreamSupport.stream(page.place().spliterator(), false), 
				StreamSupport.stream(page.transition().spliterator(), false),
				StreamSupport.stream(page.instance().spliterator(), false)
		).flatMap(Function.identity());
	}
	
	public boolean isControlFlowPlace(Place place) {
		return place.getSort().getText().equals("CaseID");
	}
	
	public boolean connects(Node nodeInQuestion, Node nodeA, Node nodeB) {
		return !nodeInQuestion.getTargetArc().isEmpty() 
				&& nodeInQuestion.getTargetArc().stream().anyMatch(any -> any.getOtherEnd(nodeInQuestion).equals(nodeA))
				&& !nodeInQuestion.getSourceArc().isEmpty() 
				&& nodeInQuestion.getSourceArc().stream().anyMatch(any -> any.getOtherEnd(nodeInQuestion).equals(nodeB));
	}

	public Stream<Arc> arcsToNodeNamed(Node source, String targetName) {
		return source.getSourceArc().stream().filter(arc -> targetName.equals(arc.getOtherEnd(source).getName().asString()));
	}
	
	public Stream<Arc> arcsFromNodeNamed(Node target, String sourceName) {
		return target.getTargetArc().stream().filter(arc -> sourceName.equals(arc.getOtherEnd(target).getName().asString()));
	}
	
	public Stream<Transition> activityTransitionsNamed(Page page, String activityName) {
		return StreamSupport.stream(page.transition().spliterator(), false).filter(transition -> transition.getName().asString().startsWith(activityName));  
	}
	
	//TODO
	public Stream<Transition> activityTransitionsForTransput(Page page, String activityName, String inputId, String inputState, String outputId, String outputState) {
		return activityTransitionsForTransput(page, activityName, Map.of(inputId, inputState), Map.of(outputId, outputState));
	}
	
	public Stream<Transition> activityTransitionsForTransput(Page page, String activityName, Map<String, String> inputStates, Map<String, String> outputStates) {
		return activityTransitionsNamed(page, activityName).filter(transition -> {
			return inputStates.entrySet().stream().allMatch(inputObject -> 
					transition.getTargetArc().stream()
						.map(arc -> arc.getPlaceNode().getName().asString())
						.anyMatch(dataPlaceName -> 
							inputObject.getKey().equals(Utils.dataPlaceElement(dataPlaceName)) 
							&& inputObject.getValue().equals(Utils.dataPlaceState(dataPlaceName))))
				&& outputStates.entrySet().stream().allMatch(outputObject -> 
					transition.getSourceArc().stream()
						.map(arc -> arc.getPlaceNode().getName().asString())
						.anyMatch(dataPlaceName -> 
							outputObject.getKey().equals(Utils.dataPlaceElement(dataPlaceName)) 
							&& outputObject.getValue().equals(Utils.dataPlaceState(dataPlaceName))));
		});
	}

	
	public Stream<Transition> transitionsFor(FlowElement activityOrStartEvent) {
		Page activityPage = pagesNamed(elementName(activityOrStartEvent)).findAny().get();
		return StreamSupport.stream(activityPage.transition().spliterator(), false);
	}
	
	
//======= Infrastructure ========
	
	@Override
	public void compileModel() {
		parseDataModel();        
        petrinet = CompilerApp.translateBPMN2CPN(bpmn, Optional.of(dataModel)); 
        parseOLCs();
	}
	
	protected void parseDataModel() {
        File dataModelFile = new File("./src/test/resources/"+modelName+".uml");
        if(dataModelFile.exists()) {
        	dataModel = DataModelParser.parse(dataModelFile);
        } else {
            dataModel = DataModel.none();
        }
	}
	
	protected void parseOLCs() {
		this.olcs = ObjectLifeCycleParser.getOLCs(dataModel, bpmn);
	}
	
	protected ObjectLifeCycle olcFor(String dataObject) {
    	return Arrays.stream(olcs)
    			.filter(olc -> olc.getClassName().equals(dataObject))
    			.findAny()
    			.get();
    }


}
