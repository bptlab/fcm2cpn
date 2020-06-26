package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import de.uni_potsdam.hpi.bpt.fcm2cpn.ModelStructureTests;

public class AssociationsProvider implements ArgumentsProvider, ArgumentContextNameResolver {
	
	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
		return associations((ModelStructureTests)context.getTestInstance().get()).map(each -> Arguments.of(each[0], each[1]));
	}
	
	public Stream<String[]> associations(ModelStructureTests test) {
		return test.dataObjectAssociations().stream();
	}
	

	@Override
	public String resolve(ExtensionContext context, Object[] args) {
		return "Association: "+Arrays.toString(args);
	}
	
}
