package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.platform.commons.util.ReflectionUtils;

public abstract class ModelConsumerTest {
	
	public String modelName;
	public BpmnModelInstance bpmn;
	
	protected void loadModel(String modelName) {
		this.modelName = modelName;

		bpmn = modelNamed(modelName);
	}
	
	protected abstract void compileModel();
	
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
			"ConferenceSimplified",
			"conference_fragments_knowledge_intensive",
			"TwoEventsInSuccessionRegression",
			"NoIOSpecification",
			"ReadAndWriteButAsCollectionReadOnlyRegression",
			"MakeGoalLowerBoundImpossibleInDifferentTaskRegression",
			"LowerBoundsCheckAtReading"
		);
	}
	
	@BeforeEach
	public void compileModelToTest(TestInfo info) {
		//TODO what if modelstotest has multiple parameters?
		//If there is a method level annotation, assume it's a single test
		info.getTestMethod()
			.map(testMethod -> testMethod.getAnnotation(ModelsToTest.class))
			.map(ModelsToTest::value)
			.ifPresent(modelsToTest -> {
				loadModel(modelsToTest[0]);
		        compileModel();
			});
		
		//If there a model parameter in test name, it's a parameterized test
//		Pattern p = Pattern.compile("model=\"(.*)\"");
//		Matcher m = p.matcher(info.getDisplayName());
//		if(m.find()) {
//			String modelToTest = m.group(1);
//			compileModel(modelToTest);
//		}
	}

	
	@TestFactory
	public Stream<DynamicContainer> forEachModel() {
		List<Method> methodsToTest = Arrays.stream(getClass().getMethods()).filter(method -> method.isAnnotationPresent(TestWithAllModels.class)).collect(Collectors.toList());
		Stream<String> modelsToTest = allModels().distinct();
		return modelsToTest.map(model -> {
			ModelConsumerTest instance = ReflectionUtils.newInstance(getClass());
			instance.loadModel(model);
			instance.compileModel();
			Stream<DynamicNode> tests = methodsToTest.stream().map(method -> ArgumentTreeTests.runMethodWithAllParameterCombinations(instance, method));
			return DynamicContainer.dynamicContainer("Model: "+model, tests);
		});
	}
	
	
	protected static BpmnModelInstance modelNamed(String modelName) {
		return Bpmn.readModelFromFile(new File(resourcePath()+modelName+".bpmn"));
	}
	
	protected static String resourcePath() {
		return "./src/test/resources/";
	}

}
