package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.platform.commons.util.ReflectionUtils;

public interface ModelConsumerTestMixin {
	
	public void compileModel(String model);
	
	public default Stream<String> allModels() {
		return Stream.empty();
	}
	
	@BeforeEach
	public default void compileModelToTest(TestInfo info) {
		//TODO what if modelstotest has multiple parameters?
		//If there is a method level annotation, assume it's a single test
		info.getTestMethod()
			.map(testMethod -> testMethod.getAnnotation(ModelsToTest.class))
			.map(ModelsToTest::value)
			.ifPresent(modelsToTest -> {
		        compileModel(modelsToTest[0]);
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
	public default Stream<DynamicContainer> forEachModel() {
		List<Method> methodsToTest = Arrays.stream(getClass().getMethods()).filter(method -> method.isAnnotationPresent(TestWithAllModels.class)).collect(Collectors.toList());
		Stream<String> modelsToTest = allModels();
		return modelsToTest.map(model -> {
			ModelConsumerTestMixin instance = ReflectionUtils.newInstance(getClass());
			instance.compileModel(model);
			Stream<DynamicNode> tests = methodsToTest.stream().map(method -> ArgumentTreeTests.runMethodWithAllParameterCombinations(instance, method));
			return DynamicContainer.dynamicContainer("Model: "+model, tests);
		});
	}

}
