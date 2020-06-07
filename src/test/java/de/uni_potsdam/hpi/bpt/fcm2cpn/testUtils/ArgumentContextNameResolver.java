package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import org.junit.jupiter.api.extension.ExtensionContext;

public interface ArgumentContextNameResolver {
	
	public String resolve(ExtensionContext context, Object[] args);

}
