package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.cpntools.accesscpn.model.Transition;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.AssociationsProvider;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIOSet;

public class AssociationTests extends ModelStructureTests {
	
	
	@TestWithAllModels
	public void testAssociationPlaceIsCreated() {
		assertEquals(1, placesNamed("associations").count(),
				"There is not exactly one place for associations");
	}
	
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testDataAssociationsBetweenReadAndWriteAreCreated(String first, String second, Activity activity, DataObjectIOSet ioSet) {
		assumeTrue(ioSet.reads(first) && ioSet.creates(second), "Activity does not read the first and create the second data object.");
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		if(arcsFromNodeNamed(transition, first).count() != 0 && arcsToNodeNamed(transition, second).count() != 0) {//Objects might not be part of transition i/o set			
			assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
			"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+elementName(activity)+" transition "+transition.getName().toString()+" for IO set "+ioSet);
		}
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testDataAssociationsBetweenParallelWritesAreCreated(String first, String second, Activity activity, DataObjectIOSet ioSet) {
		assumeTrue(ioSet.writes(first) && ioSet.writes(second), "Activity does not write both data objects.");
		assumeFalse(ioSet.associationShouldAlreadyBeInPlace(first, second), "Activity reads both data objects in the same collection/non-collection way as they are written, so an association is already in place");
		
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		if(arcsToNodeNamed(transition, first).count() != 0 && arcsToNodeNamed(transition, second).count() != 0) {//Objects might not be part of transition i/o set			
				assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
			"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+elementName(activity)+" transition "+transition.getName().toString());
		}
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testAssociationsAreCheckedWhenReading(String first, String second, Activity activity, DataObjectIOSet ioSet) {
		assumeTrue(ioSet.reads(first) && ioSet.reads(second), "Activity does not read both data objects.");
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		if(arcsFromNodeNamed(transition, first).count() != 0 && arcsFromNodeNamed(transition, second).count() != 0) {//Objects might not be part of transition i/o set			
			assertTrue(hasGuardForAssociation(transition, first, second),
					"There is no guard for association when reading objects "+first+" and "+second+" at activity "+elementName(activity)+" transition "+transition.getName().toString());
		}	
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testCheckedAssociationsAreNotDuplicated(String first, String second, Activity activity, DataObjectIOSet ioSet) {
		assumeTrue(ioSet.reads(first) && ioSet.reads(second), "Activity does not read both data objects.");
		assumeTrue(ioSet.associationShouldAlreadyBeInPlace(first, second), "A new association should be created.");
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		assertEquals(0, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
			"There is a writing association arc for objects "+first+" and "+second+" (which should already be associated) at activity "+elementName(activity)+" transition "+transition.getName().toString());
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	@ForEachBpmn(DataObject.class)
	public void testNoAssociationsForUnassociatedDataObjects(DataObject first, DataObject second) {
		String dataObjectA = elementName(first);
		String dataObjectB = elementName(second);
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
	@ForEachIOSet
	public void testUpperLimitsAreChecked(String first, String second, Activity activity, DataObjectIOSet ioSet) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int upperBound = assoc.getEnd(second).getUpperBound();
		assumeTrue(upperBound > 1 && upperBound != AssociationEnd.UNLIMITED, "No upper limit that has to be checked");//TODO will 1:1 need to be checked?
		assumeTrue(ioSet.createsAssociationBetween(first, second), "Association is not created in this activity");
		assumeTrue(!ioSet.creates(first), "First object is just created, so current cardinality is exactly 1:1 and does not have to be checked.");
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		assertEquals(1, guardsOf(transition).filter(guard -> guard.equals("(enforceUpperBound "+first+"Id "+second+" assoc "+upperBound+")")).count(),
				"There is not exactly one guard for upper limit of assoc "+assoc+" for IOSet "+ioSet+" of activity \""+elementName(activity)+"\"");
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testLowerLimitsAreChecked(String first, String second, Activity activity, DataObjectIOSet ioSet) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int lowerBound = assoc.getEnd(second).getLowerBound();
		assumeTrue(lowerBound > 1, "No lower bound that has to be checked");
		assumeTrue(ioSet.reads(second) && (ioSet.writes(first) || ioSet.reads(first)), "Association is not created in this activity");
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		if(arcsFromNodeNamed(transition, first).count() != 0 || arcsToNodeNamed(transition, first).count() != 0) {//Object might not be part of transition i/o set
			assertEquals(1, guardsOf(transition).filter(guard -> {
				String beforeIdentifier = "(enforceLowerBound ";
				String afterIdentifier = "Id "+second+" assoc "+lowerBound+")";
				if(!(guard.startsWith(beforeIdentifier) && guard.endsWith(afterIdentifier))) return false;
				String identifier = guard.replace(beforeIdentifier, "").replace(afterIdentifier, "");
				return ioSet.reads(identifier) && dataModel.getAssociation(identifier, second).map(idAssoc -> idAssoc.getEnd(identifier).getUpperBound() == 1).orElse(false);
			}).count(),
					"There is not exactly one guard for lower limit of assoc "+first+"-"+second+" at activity \""+elementName(activity)+"\"");
		}
	}
	
}
