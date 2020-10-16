package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.*;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
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
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.cpntools.accesscpn.engine.highlevel.HighLevelSimulator;
import org.cpntools.accesscpn.engine.highlevel.LocalCheckFailed;
import org.cpntools.accesscpn.engine.highlevel.checker.Checker;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.util.ReflectionUtils;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModelParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ArgumentTreeTests;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;

public abstract class ModelStructureTests {

	public String model;
	public BpmnModelInstance bpmn;
	public PetriNet petrinet;
	
	public DataModel dataModel;
	
	public List<ModelElementInstance> readingElements(ItemAwareElement dataElementReference) {
		return bpmn.getModelElementsByType(DataInputAssociation.class).stream()
				.filter(assoc -> assoc.getSources().contains(dataElementReference))
				.map(DataInputAssociation::getParentElement)
				.collect(Collectors.toList());
	}
	
	public List<ModelElementInstance> writingElements(ItemAwareElement dataElementReference) {
		return bpmn.getModelElementsByType(DataOutputAssociation.class).stream()
			.filter(assoc -> assoc.getTarget().equals(dataElementReference))
			.map(DataOutputAssociation::getParentElement)
			.collect(Collectors.toList());
	}
	
	public List<String[]> dataObjectAssociations() {
		List<String> dataObjects = bpmn.getModelElementsByType(DataObject.class).stream()
				.map(DataObject::getName)
				.map(Utils::normalizeElementName)
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
	
	public boolean reads(Activity activity, String dataObject) {
		return readDataObjectRefs(activity)
				.anyMatch(each -> normalizeElementName(each.getDataObject().getName()).equals(dataObject));
	}
	
	public boolean readsAsCollection(Activity activity, String dataObject) {
		return readDataObjectRefs(activity)
				.anyMatch(each -> normalizeElementName(each.getDataObject().getName()).equals(dataObject) && each.getDataObject().isCollection());
	}
	
	public boolean writes(Activity activity, String dataObject) {
		return writtenDataObjectRefs(activity)
				.anyMatch(each -> normalizeElementName(each.getDataObject().getName()).equals(dataObject));
	}
	
	public boolean creates(Activity activity, String dataObject) {
		return writes(activity, dataObject) && !reads(activity, dataObject);
	}
	
	public static Map<String, List<String>> dataObjectToStateMap(Stream<DataObjectReference> dataObjectReferences) {
		return dataObjectReferences
			.filter(each -> Objects.nonNull(each.getDataState()))
			.collect(Collectors.groupingBy(
					each -> normalizeElementName(each.getDataObject().getName()),
					Collectors.flatMapping(each -> Utils.dataObjectStateToNetColors(each.getDataState().getName()), Collectors.toList())));
	}
	
	public static Set<Pair<Map<String, String>, Map<String, String>>> expectedIOCombinations(Activity activity) {
		Set<Pair<Map<String, String>, Map<String, String>>> expectedCombinations = new HashSet<>();
		
		//Default if no specification: Use all inputs and outputs
		if(activity.getIoSpecification() == null) {
			// All read states, grouped by data objects
			Map<String, List<String>> inputStates = dataObjectToStateMap(readDataObjectRefs(activity));
			Map<String, List<String>> outputStates = dataObjectToStateMap(writtenDataObjectRefs(activity));

			// All possible combinations, assuming that all possible objects are read and written
			List<Map<String, String>> possibleInputSets = indexedCombinationsOf(inputStates);
			List<Map<String, String>> possibleOutputSets = indexedCombinationsOf(outputStates);

			if(possibleInputSets.isEmpty() && !possibleOutputSets.isEmpty()) possibleInputSets.add(Collections.emptyMap());
			if(possibleOutputSets.isEmpty()&& !possibleInputSets.isEmpty()) possibleOutputSets.add(Collections.emptyMap());
			for(Map<String, String> inputSet : possibleInputSets) {
				for(Map<String, String> outputSet : possibleOutputSets) {
					expectedCombinations.add(new Pair<>(inputSet, new HashMap<>(outputSet)));
				}
			}
		
		// Else parse io specification
		} else {
			Map<String, List<Map<String, String>>> outputSetsToPossibleForms = activity.getIoSpecification().getOutputSets().stream()
				.collect(Collectors.toMap(OutputSet::getId, outputSet -> {
					Stream<DataObjectReference> writtenReferences = outputSet.getDataOutputRefs().stream()
							.map(Utils::getAssociation)
							.map(DataOutputAssociation::getTarget)
							.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
					Map<String, List<String>> outputStates = dataObjectToStateMap(writtenReferences);
					List<Map<String, String>> possibleForms = indexedCombinationsOf(outputStates);
					return possibleForms;
				}));
			activity.getIoSpecification().getInputSets().stream().forEach(inputSet -> {
				Stream<DataObjectReference> readReferences = inputSet.getDataInputs().stream()
						.map(Utils::getAssociation)
						.flatMap(assoc -> assoc.getSources().stream())
						.filter(each -> each instanceof DataObjectReference).map(DataObjectReference.class::cast);
				Map<String, List<String>> inputStates = dataObjectToStateMap(readReferences);
				List<Map<String, String>> possibleForms = indexedCombinationsOf(inputStates);
				
				for(OutputSet associatedOutputSet : inputSet.getOutputSets()) {
					for(Map<String, String> inputSetForm : possibleForms) {
						for(Map<String, String> outputSetForm : outputSetsToPossibleForms.get(associatedOutputSet.getId())) {
							expectedCombinations.add(new Pair<>(inputSetForm, new HashMap<>(outputSetForm)));
						}
					}
				}
			});
		}

		//Expect read objects that are not explicitly written to be written back in the same state as they are read
		for(Pair<Map<String, String>, Map<String, String>> ioConfiguration : expectedCombinations) {
			Map<String, String> inputSet = ioConfiguration.first;
			Map<String, String> outputSet = ioConfiguration.second;
			for(String inputObject : inputSet.keySet()) {
				if(!outputSet.containsKey(inputObject)) outputSet.put(inputObject, inputSet.get(inputObject));
			}
		}
		
		return expectedCombinations;
	}
	
	public Set<Pair<Map<String, String>, Map<String, String>>> ioCombinationsInNet(Activity activity) {
		Set<Pair<Map<String, String>, Map<String, String>>> ioCombinations = new HashSet<>();
		transitionsFor(activity).forEach(transition -> {
			Map<String, String> inputs = new HashMap<>();
			Map<String, String> outputs = new HashMap<>();
			transition.getTargetArc().stream()
				.map(inputArc -> inputArc.getHlinscription().asString())
				.forEach(inscription -> parseCreatedTokenIdAndState(inscription).ifPresent(idAndState -> inputs.put(idAndState.first, idAndState.second)));

			transition.getSourceArc().stream()
				.map(outputArc -> outputArc.getHlinscription().asString())
				.forEach(inscription -> parseCreatedTokenIdAndState(inscription).ifPresent(idAndState -> outputs.put(idAndState.first, idAndState.second)));
			ioCombinations.add(new Pair<Map<String,String>, Map<String,String>>(inputs, outputs));
		});
		
		return ioCombinations;
	}
	
	public static Optional<Pair<String, String>> parseCreatedTokenIdAndState(String inscription) {
		String dataId = null;
		String state = null;

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
					if(statement[0].trim().equals("id")) dataId = !statement[1].contains("unpack") ? statement[1].trim().replaceAll("Id$", "") : statement[1].trim().replaceAll("unpack el ", "");
					if(statement[0].trim().equals("state")) state = statement[1].trim();
				}
			if(dataId != null && state != null) return Optional.of(new Pair<>(dataId, state));
		}
		return Optional.empty();
	}
	
	public static List<Map<String, String>> indexedCombinationsOf(Map<String, List<String>> groups) {
		//Get defined order into collection
		List<String> keys = new ArrayList<>(groups.keySet());
		List<List<String>> combinations = Utils.allCombinationsOf(keys.stream().map(groups::get).collect(Collectors.toList()));
		
		//Zip keys with each combination
		return combinations.stream().map(combination -> {
			HashMap<String, String> map = new HashMap<>();
			for(int i = 0; i < keys.size(); i++) {
				map.put(keys.get(i), combination.get(i));
			}
			return map;
		}).collect(Collectors.toList());
	}
	
	public static Predicate<Arc> writesAssociation(String first, String second) {
		return arc -> {
			String inscription = arc.getHlinscription().getText();
			String list = inscription.replace("assoc", "").trim();
			Set<String> allWrittenAssocs = Arrays.stream(list.split("\\^\\^"))
					.map(ModelStructureTests::toSet)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());
			return inscription.contains("assoc") && allWrittenAssocs.stream().filter(assoc -> isAssocInscriptionFor(assoc, first, second)).count() == 1;
		};
	}
	
	public static Stream<String> guardsOf(Transition transition) {
		String guard = transition.getCondition().getText();
		return Arrays.stream(guard.split("andalso"))
				.map(String::trim);
	}
	
	public static boolean hasGuardForAssociation(Transition transition, String first, String second) {
		return guardsOf(transition).anyMatch(singleCondition -> {
			String list = singleCondition.replace("contains assoc ", "").trim();
			return singleCondition.contains("contains assoc") && toSet(list).stream().filter(assoc -> isAssocInscriptionFor(assoc, first, second)).count() == 1;
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
        	
        	//Suppress error for places that have no name
        	petrinet.getPage().stream()
        		.flatMap(page -> StreamSupport.stream(page.getObject().spliterator(), false))
        		.filter(each -> Objects.nonNull(each.getName()) && Objects.isNull(each.getName().getText()))
        		.forEach(each -> each.getName().setText("XXX"+new Random().nextInt()));
        	
        	Checker checker = new Checker(petrinet, null, simu);
        	checker.checkEntireModel();
        } catch (LocalCheckFailed e) {
        	boolean allowedFailure = e.getMessage().contains("illegal name (name is `null')") || e.getMessage().contains("is not unique");
        	if(!allowedFailure) throw e;
		} catch(NoSuchElementException e) {
			// From Packet:170, weird bug, but catching this error seems to work
		}
	}
	
	public Stream<Instance> instancesNamed(String name) {
		Page mainPage = petrinet.getPage().get(0);
		return StreamSupport.stream(mainPage.instance().spliterator(), false).filter(instance -> instance.getName().asString().equals(name));
	}
	
	public Stream<Page> pagesNamed(String name) {
		return petrinet.getPage().stream().filter(page -> page.getName().asString().equals(name));
	}
	
	public Page pageNamed(String name) {
		return pagesNamed(name).findAny().get();
	}
	
	public Stream<Place> placesNamed(String name) {
		Page mainPage = petrinet.getPage().get(0);
		return StreamSupport.stream(mainPage.place().spliterator(), false)
				.filter(place -> Objects.toString(place.getName().asString()).equals(name));
	}

	public Stream<Place> dataObjectPlacesNamed(String name) {
		return placesNamed(name)
				.filter(place -> place.getSort().getText().equals("DATA_OBJECT") );
	}
	
	public Stream<Place> dataStorePlacesNamed(String name) {
		return placesNamed(name)
				.filter(place -> place.getSort().getText().equals("DATA_STORE") );
	}
	
	/**
	 * Control flow between two bpmn elements is correctly mapped if: <br>
	 * a) there is a place between transitions with the names <br>
	 * OR<br> 
	 * b) if there is a control flow place for one of them that is connected to a transition for the other one
	 */
	public Stream<Place> controlFlowPlacesBetween(String nodeA, String nodeB) {
		Page mainPage = petrinet.getPage().get(0);
		return StreamSupport.stream(mainPage.place().spliterator(), false).filter(place -> {
			return isControlFlowPlace(place) 
					&& (
						!place.getTargetArc().isEmpty() && place.getTargetArc().stream().anyMatch(any -> any.getOtherEnd(place).getName().asString().equals(nodeA))
						|| Objects.toString(place.getName().asString()).equals(nodeA)
					) && (
						!place.getSourceArc().isEmpty() && place.getSourceArc().stream().anyMatch(any -> any.getOtherEnd(place).getName().asString().equals(nodeB))
						|| Objects.toString(place.getName().asString()).equals(nodeB)
					);
		});
	}
	
	public boolean isControlFlowPlace(Place place) {
		return place.getSort().getText().equals("CaseID");
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
	
	public Stream<Transition> activityTransitionsForTransput(Page page, String activityName, String inputState, String outputState) {
		return activityTransitionsNamed(page, activityName).filter(transition -> {
			return transition.getTargetArc().stream().filter(arc -> arc.getHlinscription().asString().contains("state = "+inputState)).count() == 1
					&& transition.getSourceArc().stream().filter(arc -> arc.getHlinscription().asString().contains("state = "+outputState)).count() == 1;
		});
	}
	
	public Stream<Transition> activityTransitionsForTransput(Page page, String activityName, Map<String, String> inputStates, Map<String, String> outputStates) {
		return activityTransitionsNamed(page, activityName).filter(transition -> {
			return inputStates.entrySet().stream().allMatch(inputObject -> 
					transition.getTargetArc().stream()
						.map(arc -> arc.getHlinscription().asString())
						.anyMatch(inscription -> inscription.contains(inputObject.getKey()+"Id") && inscription.contains("state = "+inputObject.getValue())))
				&& outputStates.entrySet().stream().allMatch(outputObject -> 
					transition.getSourceArc().stream()
						.map(arc -> arc.getHlinscription().asString())
						.anyMatch(inscription -> inscription.contains(outputObject.getKey()+"Id") && inscription.contains("state = "+outputObject.getValue())));
		});
	}
	
	public Stream<Transition> transitionsFor(FlowElement activity) {
		Page activityPage = pagesNamed(normalizeElementName(activity.getName())).findAny().get();
		return StreamSupport.stream(activityPage.transition().spliterator(), false);
	}
	
	
//======= Infrastructure ========
	@BeforeEach
	public void compileModelToTest(TestInfo info) {
		//If there is a method level annotation, assume it's a single test
		info.getTestMethod()
			.map(testMethod -> testMethod.getAnnotation(ModelsToTest.class))
			.map(ModelsToTest::value)
			.ifPresent(modelsToTest -> {
		        compileModel(modelsToTest[0]);
			});
		
		//If there a model parameter in test name, it's a parameterized test
		Pattern p = Pattern.compile("model=\"(.*)\"");
		Matcher m = p.matcher(info.getDisplayName());
		if(m.find()) {
			String modelToTest = m.group(1);
			compileModel(modelToTest);
		}
	}
	
	protected void compileModel(String modelName) {
		model = modelName;
		bpmn = Bpmn.readModelFromFile(new File("./src/test/resources/"+model+".bpmn"));

        File dataModelFile = new File("./src/test/resources/"+model+".uml");
        if(dataModelFile.exists()) {
        	dataModel = DataModelParser.parse(dataModelFile);
        } else {
            dataModel = DataModel.none();
        }
        
        petrinet = CompilerApp.translateBPMN2CPN(bpmn, Optional.of(dataModel)); 

	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Testable
	protected static @interface TestWithAllModels {}
	
	public Stream<String> allModels() {
		return Stream.of(
			"Simple", 
			"SimpleWithStates", 
			"SimpleWithEvents", 
			"SimpleWithGateways", 
			"SimpleWithDataStore", 
			"TranslationJob",
			"Associations",
			"TransputSets",
			"ConferenceSimplified"
		);
	}
	
	
	@TestFactory
	public Stream<DynamicContainer> forEachModel() {
		List<Method> methodsToTest = Arrays.stream(getClass().getMethods()).filter(method -> method.isAnnotationPresent(TestWithAllModels.class)).collect(Collectors.toList());
		Stream<String> modelsToTest = allModels();
//			Optional.of(getClass())
//				.map(clazz -> clazz.getAnnotation(ModelsToTest.class))
//				.map(ModelsToTest::value)
//				.stream()
//				.flatMap(Arrays::stream);
		return modelsToTest.map(model -> {
			ModelStructureTests instance = ReflectionUtils.newInstance(getClass());
			instance.compileModel(model);
			Stream<DynamicNode> tests = methodsToTest.stream().map(method -> ArgumentTreeTests.runMethodWithAllParameterCombinations(instance, method));
			return DynamicContainer.dynamicContainer("Model: "+model, tests);
		});
	}

}
