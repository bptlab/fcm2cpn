package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.camunda.bpm.model.bpmn.instance.BpmnModelElementInstance;
import org.junit.jupiter.params.provider.ArgumentsSource;

@Target({ ElementType.ANNOTATION_TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(BpmnElementProvider.class)
@Repeatable(ForEachBpmns.class)
public @interface ForEachBpmn {
	Class<? extends BpmnModelElementInstance> value();
}
