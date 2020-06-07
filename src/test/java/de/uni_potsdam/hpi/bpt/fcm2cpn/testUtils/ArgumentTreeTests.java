package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ReflectionUtils;

public class ArgumentTreeTests {
	
	private final Object wrappedTestObject;
	
	private ArgumentTreeTests(Object wrappedTestObject) {
		this.wrappedTestObject = wrappedTestObject;
	}
	
	public static DynamicNode runMethodWithAllParameterCombinations(Object testObject, Method method) {
		List<Annotation> argumentsSources = argumentSources(method);
		return new ArgumentTreeTests(testObject).resolveArguments(method, argumentsSources, Collections.emptyList(), "Method: "+method.getName());
	}
	
	
	@SuppressWarnings("unchecked")
	private DynamicNode resolveArguments(Method method, List<Annotation> remainingSources, List<Object> currentArguments, String contextName) {
		if(remainingSources.isEmpty()) {
			return DynamicTest.dynamicTest(contextName, () -> method.invoke(wrappedTestObject, currentArguments.toArray()));
		} else {
			Annotation nextSource = remainingSources.get(0);
			Stream<Object[]> arguments;
			try {
				ExtensionContext context = createContext(method, currentArguments);
				ArgumentsProvider provider = providerFor(nextSource);
				if(provider instanceof AnnotationConsumer<?>)((AnnotationConsumer<Annotation>) provider).accept(nextSource);
				arguments = provider.provideArguments(context).map(Arguments::get);
				
				return DynamicContainer.dynamicContainer(contextName, arguments.map(args -> {
					List<Object> newArguments = new ArrayList<>();
					newArguments.addAll(currentArguments);
					newArguments.addAll(Arrays.asList(args));
					List<Annotation> newSources = new ArrayList<>();
					newSources.addAll(remainingSources);
					assert newSources.remove(nextSource);
					String newContextName = provider instanceof ArgumentContextNameResolver ? ((ArgumentContextNameResolver) provider).resolve(context, args) : Arrays.toString(args);
					return resolveArguments(method, newSources, newArguments, newContextName);
				}));
			} catch (Exception e) {
				throw new RuntimeException("Could not resolve parameters", e);
			}
		}
	}
	
	private static List<Annotation> argumentSources(Method annotatedMethod) {
		Annotation[] annotations = annotatedMethod.getAnnotations();
		Stream<Annotation> directSources =  Arrays.stream(annotations)
				.filter(annotation -> isArgumentsSourceType(annotation.getClass()));
		Stream<Annotation> repeatedSources =  Arrays.stream(annotations)
				.map(annotation -> 
					Arrays.stream(annotation.getClass().getDeclaredMethods())
						.filter(method -> method.getName().equals("value"))
						.filter(method -> Optional.ofNullable(method.getReturnType().getComponentType()).map(composedType -> isArgumentsSourceType(composedType)).orElse(false))
						.map(method -> {
							try {
								return (Annotation[]) method.invoke(annotation);
							} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
								e.printStackTrace();
								return null;
							}
						})
						.filter(Objects::nonNull)
						.findAny()
				).flatMap(Optional::stream)
				.flatMap(Arrays::stream);
		return Stream.concat(directSources, repeatedSources).collect(Collectors.toList());
	}
	
	private static boolean isArgumentsSourceType(Class<?> annotationType) {
		return annotationType.equals(ArgumentsSource.class) || AnnotationUtils.isAnnotated(annotationType, ArgumentsSource.class);
	}
	
	private static ArgumentsProvider providerFor(Annotation sourceAnnotation) {
		ArgumentsSource source = sourceAnnotation instanceof ArgumentsSource ? 
				(ArgumentsSource)sourceAnnotation 
				: AnnotationUtils.findAnnotation(sourceAnnotation.getClass(), ArgumentsSource.class).get();
		return ReflectionUtils.newInstance(source.value());
	}
	
	private ExtensionContext createContext(Method method, List<Object> additionalIdentifiers) {
		return new ExtensionContext() {
			
			@Override
			public void publishReportEntry(Map<String, String> map) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public String getUniqueId() {
				return getClass().getSimpleName()+method.getName()+additionalIdentifiers.toString();
			}
			
			@Override
			public Optional<Method> getTestMethod() {
				return Optional.of(method);
			}
			
			@Override
			public Optional<TestInstances> getTestInstances() {
				return null;
			}
			
			@Override
			public Optional<Lifecycle> getTestInstanceLifecycle() {
				return null;
			}
			
			@Override
			public Optional<Object> getTestInstance() {
				return Optional.of(wrappedTestObject);
			}
			
			@Override
			public Optional<Class<?>> getTestClass() {
				return Optional.of(getClass());
			}
			
			@Override
			public Set<String> getTags() {
				return null;
			}
			
			@Override
			public Store getStore(Namespace namespace) {
				return null;
			}
			
			@Override
			public ExtensionContext getRoot() {
				return null;
			}
			
			@Override
			public Optional<ExtensionContext> getParent() {
				return null;
			}
			
			@Override
			public Optional<Throwable> getExecutionException() {
				return null;
			}
			
			@Override
			public Optional<AnnotatedElement> getElement() {
				return null;
			}
			
			@Override
			public String getDisplayName() {
				return getUniqueId();
			}
			
			@Override
			public Optional<String> getConfigurationParameter(String key) {
				return null;
			}
		};
	}

}
