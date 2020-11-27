package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ArgumentContextNameResolver;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ArgumentTreeTests.ArgumentTreeExtensionContext;

/**
 * Experimental class to add IO Sets as test parameter
 * Depends on being used after an ForEachBpmn for activities and in ArgumentTreeTests
 * @author Leon Bein
 *
 */
public class IOContextProvider implements ArgumentsProvider, ArgumentContextNameResolver {
	
	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
		Activity activity = ((ArgumentTreeExtensionContext) context).getCurrentArguments().stream()
				.filter(parameter -> parameter instanceof Activity)
				.map(Activity.class::cast)
				.findAny().get();
		
		return GeneralModelStructureTests.ioAssociationCombinations(activity).stream().map(io -> Arguments.of(io));
//		return ((ModelStructureTests)context.getTestInstance().get()).bpmn.getModelElementsByType(Activity.class).stream().flatMap(activity -> {
//			return GeneralModelStructureTests.ioAssociationCombinations(activity).stream()
//					.map(ioSet -> );
//		});
	}
	

	@Override
	public String resolve(ExtensionContext context, Object[] args) {
		return "IOSet: "+args[0];
	}
	
}
