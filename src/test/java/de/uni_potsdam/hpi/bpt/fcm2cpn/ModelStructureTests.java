package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ReflectionUtils;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ArgumentContextNameResolver;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;

public abstract class ModelStructureTests {

	public String model;
	public BpmnModelInstance bpmn;
	public PetriNet petrinet;
	
	public <T extends ModelElementInstance> void forEach(Class<T> elementClass, Consumer<? super T> testBody) {
		bpmn.getModelElementsByType(elementClass).forEach(testBody);
	}
	
	public void checkNet() throws Exception {
        try {
        	HighLevelSimulator simu = HighLevelSimulator.getHighLevelSimulator();
        	Checker checker = new Checker(petrinet, null, simu);
    		checker.checkEntireModel();
        } catch (LocalCheckFailed e) {
        	boolean allowedFailure = e.getMessage().contains("illegal name (name is `null')");
        	if(!allowedFailure) throw e;
		}
	}
	
	public Stream<Instance> instancesNamed(String name) {
		Page mainPage = petrinet.getPage().get(0);
		return StreamSupport.stream(mainPage.instance().spliterator(), true).filter(instance -> instance.getName().asString().equals(name));
	}
	
	public Stream<Page> pagesNamed(String name) {
		return petrinet.getPage().stream().filter(page -> page.getName().asString().equals(name));
	}

	public Stream<Place> dataObjectPlacesNamed(String name) {
		Page mainPage = petrinet.getPage().get(0);
		return StreamSupport.stream(mainPage.place().spliterator(), true).filter(place -> {
			return place.getSort().getText().equals("DATA_OBJECT") 
					&& place.getName().asString().equals(name);
		});
	}
	
	public Stream<Place> controlFlowPlacesBetween(String nodeA, String nodeB) {
		Page mainPage = petrinet.getPage().get(0);
		return StreamSupport.stream(mainPage.place().spliterator(), true).filter(place -> {
			return place.getSort().getText().equals("CaseID") 
					&& place.getTargetArc().get(0).getOtherEnd(place).getName().asString().equals(nodeA)
					&& place.getSourceArc().get(0).getOtherEnd(place).getName().asString().equals(nodeB);
		});
	}

	public Stream<Arc> arcsToNodeNamed(Node source, String targetName) {
		return source.getSourceArc().stream().filter(arc -> arc.getOtherEnd(source).getName().asString().equals(targetName));
	}
	
	public Stream<Arc> arcsFromNodeNamed(Node source, String targetName) {
		return source.getTargetArc().stream().filter(arc -> arc.getOtherEnd(source).getName().asString().equals(targetName));
	}
	
	public Stream<Transition> activityTransitionsNamed(Page page, String activityName) {
		return StreamSupport.stream(page.transition().spliterator(), true).filter(transition -> transition.getName().asString().startsWith(activityName));  
	}
	
	public Stream<Transition> activityTransitionsForTransput(Page page, String activityName, String inputState, String outputState) {
		return activityTransitionsNamed(page, activityName).filter(transition -> {
			return transition.getTargetArc().stream().filter(arc -> arc.getHlinscription().asString().contains("state = "+inputState)).count() == 1
					&& transition.getSourceArc().stream().filter(arc -> arc.getHlinscription().asString().contains("state = "+outputState)).count() == 1;
		});
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
        petrinet = CompilerApp.translateBPMN2CPN(bpmn);
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Testable
	protected static @interface TestWithAllModels {}
	
	
	@TestFactory
	public Stream<DynamicContainer> forEachModel() {
		List<Method> methodsToTest = Arrays.stream(getClass().getMethods()).filter(method -> method.isAnnotationPresent(TestWithAllModels.class)).collect(Collectors.toList());
		Stream<String> modelsToTest = Optional.of(getClass())
				.map(clazz -> clazz.getAnnotation(ModelsToTest.class))
				.map(ModelsToTest::value)
				.stream()
				.flatMap(Arrays::stream);
		return modelsToTest.map(model -> {
			ModelStructureTests instance = ReflectionUtils.newInstance(getClass());
			instance.compileModel(model);
			Stream<DynamicNode> tests = methodsToTest.stream().map(method -> {
				List<Annotation> argumentsSources = argumentSources(method);
				return instance.resolveArguments(method, argumentsSources, Collections.emptyList(), "Method: "+method.getName());
			});
			return DynamicContainer.dynamicContainer("Model: "+model, tests);
		});
	}
	
	@SuppressWarnings("unchecked")
	public DynamicNode resolveArguments(Method method, List<Annotation> remainingSources, List<Object> currentArguments, String contextName) {
		if(remainingSources.isEmpty()) {
			return DynamicTest.dynamicTest(contextName, () -> method.invoke(this, currentArguments.toArray()));
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
	
	public static List<Annotation> argumentSources(Method annotatedMethod) {
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
	
	public static boolean isArgumentsSourceType(Class<?> annotationType) {
		return annotationType.equals(ArgumentsSource.class) || AnnotationUtils.isAnnotated(annotationType, ArgumentsSource.class);
	}
	
	public static ArgumentsProvider providerFor(Annotation sourceAnnotation) {
		ArgumentsSource source = sourceAnnotation instanceof ArgumentsSource ? 
				(ArgumentsSource)sourceAnnotation 
				: AnnotationUtils.findAnnotation(sourceAnnotation.getClass(), ArgumentsSource.class).get();
		return ReflectionUtils.newInstance(source.value());
	}
	
	public ExtensionContext createContext(Method method, List<Object> additionalIdentifiers) {
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
				return Optional.of(ModelStructureTests.this);
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
