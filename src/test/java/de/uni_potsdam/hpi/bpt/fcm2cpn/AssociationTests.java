package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.AssociationsProvider;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;

public class AssociationTests extends ModelStructureTests {
	
	@TestWithAllModels
	public void testAssociationPlaceIsCreated() {
		assertEquals(1, placesNamed("associations").count(),
				"There is not exactly one place for associations");
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testDataAssociationsBetweenReadAndWriteAreCreated(String first, String second, Activity activity) {
		assumeTrue(reads(activity, first) && writes(activity, second), "Activity does not read the first and write the second data object.");
		assumeFalse(reads(activity, first) && reads(activity, second), "Activity reads both data objects, so an association is already in place");
		
		transitionsFor(activity).forEach(transition -> {
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName())+" transition "+transition.getName().toString());
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testDataAssociationsBetweenParallelWritesAreCreated(String first, String second, Activity activity) {
		assumeTrue(writes(activity, first) && writes(activity, second), "Activity does not write both data objects.");
		assumeFalse(reads(activity, first) && reads(activity, second), "Activity reads both data objects, so an association is already in place");
		
		transitionsFor(activity).forEach(transition -> {
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName())+" transition "+transition.getName().toString());
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testAssociationsAreCheckedWhenReading(String first, String second, Activity activity) {
		assumeTrue(reads(activity, first) && reads(activity, second), "Activity does not read both data objects.");
		
		transitionsFor(activity).forEach(transition -> {
			assertTrue(hasGuardForAssociation(transition, first, second),
				"There is no guard for association when reading objects "+first+" and "+second+" at activity "+normalizeElementName(activity.getName())+" transition "+transition.getName().toString());
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testCheckedAssociationsAreNotDuplicated(String first, String second, Activity activity) {
		assumeTrue(reads(activity, first) && reads(activity, second), "Activity does not read both data objects.");
		transitionsFor(activity).forEach(transition -> {
			assertEquals(0, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is a writing association arc for objects "+first+" and "+second+" (which should already be associated) at activity "+normalizeElementName(activity.getName())+" transition "+transition.getName().toString());
		});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	@ForEachBpmn(DataObject.class)
	public void testNoAssociationsForUnassociatedDataObjects(DataObject first, DataObject second) {
		String dataObjectA = normalizeElementName(first.getName());
		String dataObjectB = normalizeElementName(second.getName());
		assumeFalse(dataModel.isAssociated(dataObjectA, dataObjectB));
		long numberOfAssociationArcs = petrinet.getPage().stream()
			.flatMap(page -> page.getArc().stream())
			.filter(writesAssociation(dataObjectA+"Id", dataObjectB+"Id"))
			.count();
		assertEquals(0, numberOfAssociationArcs, "There are association write arcs for data objects "+dataObjectA+" and "+dataObjectB+" though they are not associated");
	}

	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testUpperLimitsAreChecked(String first, String second, Activity activity) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int upperBound = assoc.getEnd(second).getUpperBound();
		assumeTrue(upperBound > 1 && upperBound != AssociationEnd.UNLIMITED, "No upper limit that has to be checked");//TODO will 1:1 need to be checked?
		assumeTrue(creates(activity, second) && (writes(activity, first) || reads(activity, first)), "Association is not created in this activity");
		transitionsFor(activity).forEach(transition -> {
			assertEquals(1, guardsOf(transition).filter(guard -> guard.equals("(enforceUpperBound "+first+"Id "+second+" assoc "+upperBound+")")).count(),
					"There is not exactly one guard for upper limit of assoc "+first+"-"+second+" at activity \""+elementName(activity)+"\"");
		});
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testLowerLimitsAreChecked(String first, String second, Activity activity) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int lowerBound = assoc.getEnd(second).getLowerBound();
		assumeTrue(lowerBound > 1, "No lower bound that has to be checked");
		assumeTrue(reads(activity, second) && (writes(activity, first) || reads(activity, first)), "Association is not created in this activity");
		transitionsFor(activity).forEach(transition -> {
			assertEquals(1, guardsOf(transition).filter(guard -> {
				String beforeIdentifier = "(enforceLowerBound ";
				String afterIdentifier = "Id "+second+" assoc "+lowerBound+")";
				if(!(guard.startsWith(beforeIdentifier) && guard.endsWith(afterIdentifier))) return false;
				String identifier = guard.replace(beforeIdentifier, "").replace(afterIdentifier, "");
				return reads(activity, identifier) && dataModel.getAssociation(identifier, second).map(idAssoc -> idAssoc.getEnd(identifier).getUpperBound() == 1).orElse(false);
			}).count(),
					"There is not exactly one guard for lower limit of assoc "+first+"-"+second+" at activity \""+elementName(activity)+"\"");
		});
	}
	
}