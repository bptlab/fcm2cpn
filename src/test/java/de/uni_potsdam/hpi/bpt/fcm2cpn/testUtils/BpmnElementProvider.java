package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

public class BpmnElementProvider implements ArgumentsProvider, AnnotationConsumer<ForEachBpmn>, ArgumentContextNameResolver {
	
	private Class<? extends BaseElement> elementClass;

	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
		return elements((ModelConsumerTest)context.getTestInstance().get(), elementClass)
			.map(Arguments::of);
	}
	
	public <T extends BaseElement> Stream<T> elements(ModelConsumerTest test, Class<T> clazz) {
		return test.bpmn.getModelElementsByType(clazz).stream();
	}

	@Override
	public void accept(ForEachBpmn t) {
		elementClass = t.value();
	}

	@Override
	public String resolve(ExtensionContext context, Object[] args) {
		return elementClass.getSimpleName()+": "+Arrays.stream(args).map(each -> ((BaseElement)each).getId()).collect(Collectors.joining(", "));
	}
	
}
