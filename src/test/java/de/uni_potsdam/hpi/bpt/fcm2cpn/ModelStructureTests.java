package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;

public abstract class ModelStructureTests {

	protected BpmnModelInstance bpmn;
	protected PetriNet petrinet;
	
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
	
	@ParameterizedTest(name="#{index} model=\"{0}\"")
	@Retention(RetentionPolicy.RUNTIME)
	@ArgumentsSource(TestedModelsProvider.class)
	protected static @interface TestWithAllModels {}
	
	protected static final class TestedModelsProvider implements ArgumentsProvider {
		
		@Override
		public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
			return context.getTestClass()
				.map(testMethod -> testMethod.getAnnotation(ModelsToTest.class))
				.map(ModelsToTest::value)
				.stream()
				.flatMap(Arrays::stream)
				.map(Arguments::of);
		}
	};
	
	protected void compileModel(String modelName) {
		bpmn = Bpmn.readModelFromFile(new File("./src/test/resources/"+modelName+".bpmn"));
        petrinet = CompilerApp.translateBPMN2CPN(bpmn);
	}

}
