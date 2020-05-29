package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.BpmnModelElementInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;

public class BpmnElementProvider implements ArgumentsProvider  {

	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
		return context.getTestMethod()
			.map(testMethod -> testMethod.getAnnotation(ForEachBpmn.class))
			.map(ForEachBpmn::value)
			.stream()
			.flatMap(clazz -> elements((ModelStructureTests)context.getTestInstance().get(), clazz))
			.map(Arguments::of);
	}
	
	public <T extends BpmnModelElementInstance> Stream<T> elements(ModelStructureTests test, Class<T> clazz) {
		return test.bpmn.getModelElementsByType(clazz).stream();
	}
	
}
