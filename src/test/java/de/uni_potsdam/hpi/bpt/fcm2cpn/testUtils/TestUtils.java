package de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TestUtils {
	
	public static <T> void assertExactlyOne(Stream<T> elementsToCheck, Predicate<T> predicate, String message) {
		assertEquals(1, elementsToCheck.filter(predicate).count(), message);
	}
	
	public static <T> void assertExactlyOne(Collection<T> elementsToCheck, Predicate<T> predicate, String message) {
		assertExactlyOne(elementsToCheck.stream(), predicate, message);
	}
	
	
	public static <T> void assertExactlyOne(Stream<T> elementsToCheck, String message) {
		assertExactlyOne(elementsToCheck, x -> true, message);
	}
	
	public static <T> void assertExactlyOne(Collection<T> elementsToCheck, String message) {
		assertExactlyOne(elementsToCheck.stream(), message);
	}

}
