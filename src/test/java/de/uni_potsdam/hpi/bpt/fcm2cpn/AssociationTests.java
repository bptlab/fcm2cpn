package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashSet;
import java.util.Set;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.cpntools.accesscpn.model.Transition;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.AssociationsProvider;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIdIOSet;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

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
	public void testDataAssociationsBetweenReadAndWriteAreCreated(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
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
	public void testDataAssociationsBetweenParallelWritesAreCreated(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
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
	public void testAssociationsAreCheckedWhenReading(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
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
	public void testCheckedAssociationsAreNotDuplicated(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
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
	public void testUpperLimitsAreChecked(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
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
	/** Lower bounds must be checked if associated objects are read together*/
	public void testLowerLimitsAreCheckedWhenReadTogetherWithCollection(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int lowerBound = assoc.getEnd(second).getLowerBound();
		assumeTrue(lowerBound > 1, "No lower bound that has to be checked");
		assumeTrue(ioSet.reads(first) && ioSet.reads(second), "no "+first+" is read together with "+second);
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		assertEquals(1, guardsOf(transition).map(AssociationTests::removeComments)
				.filter(guard -> guard.equals("(enforceLowerBound "+first+"Id "+second+" assoc "+lowerBound+")")).count(),
				"There is not exactly one guard for lower limit of assoc "+first+"-"+second+" at activity \""+elementName(activity)+"\"");
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	/** Lower bounds must be changed at creation as in "are there enough B to create an A?"; number of B is determined via identifier*/
	public void testLowerLimitsAreCheckedWhenNewObjectIsCreated(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int lowerBound = assoc.getEnd(second).getLowerBound();
		assumeTrue(lowerBound > 1, "No lower bound that has to be checked");
		assumeTrue(ioSet.creates(first) && ioSet.reads(second), "Association is not created in this activity");
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		if(arcsFromNodeNamed(transition, first).count() != 0 || arcsToNodeNamed(transition, first).count() != 0) {//Object might not be part of transition i/o set
			assertEquals(1, guardsOf(transition).map(AssociationTests::removeComments).filter(guard -> {
				String beforeIdentifier = "(enforceLowerBound ";
				String afterIdentifier = "Id "+second+" assoc "+lowerBound+")";
				String identifier = guard.replace(beforeIdentifier, "").replace(afterIdentifier, "");
				return guard.startsWith(beforeIdentifier) && guard.endsWith(afterIdentifier) && isValidCollectionIdentifier(identifier, second, ioSet);
			}).count(),
					"There is not exactly one guard for lower limit "+lowerBound+" of assoc "+first+"-"+second+" at activity \""+elementName(activity)+"\"");
		}
	}
	
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testGoalCardinalitiesAreCheckedOnStateChange(Activity activity,  DataObjectIdIOSet ioSet) {
		var stateMap = ioAssociationsToStateMaps(ioSet);
		Transition transition = transitionForIoCombination(stateMap, activity).get();
		
		var dataObjectStateChangesWithReducedUpdateability = ioSet.stateChanges().stream().flatMap(stateChange -> {
			ObjectLifeCycle olc = olcFor(stateChange.first.dataElementName());
			Set<AssociationEnd> removedUpdateableAssociations = new HashSet<>(olc.getState(stateChange.first.getStateName()).get().getUpdateableAssociations());
			removedUpdateableAssociations.removeAll(olc.getState(stateChange.second.getStateName()).get().getUpdateableAssociations());
			return removedUpdateableAssociations.stream().map(removedAssoc -> new Pair<>(stateChange, removedAssoc));
		});
		
		var dataObjectStateChangesWithReducedUpdateabilityAndTightGoalLowerBounds = dataObjectStateChangesWithReducedUpdateability
				.filter(x -> x.second.hasTightGoalLowerBound());
		
		dataObjectStateChangesWithReducedUpdateabilityAndTightGoalLowerBounds.forEach(x -> {
			var stateChange = x.first;
			var removedAssociation = x.second;
			String dataObjectName = stateChange.first.dataElementName();
			assertTrue(transition.getCondition().getText().contains(IOSetCompiler.GOAL_CARDINALITY_COMMENT), 
					"Activity transition "+transition.getName().asString()+" for io set "+ioSet+" does not check for goal lower bound between "+dataObjectName+" and "+removedAssociation.getDataObject()
					+" although "+dataObjectName+" changes state from "+stateChange.first.getStateName()+" to "+stateChange.second.getStateName()+" where no new associations can be created");
			//TODO actually check for correct statement when goal cardinalities are implemented
		});
	}
	
	public void testCheckedGoalCardinalitiesComeFromStateChange() {
		//TODO can only be created when goal cardinalities are implemented
	}
	
}
