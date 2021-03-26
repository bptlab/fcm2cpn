package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

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
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIOSet.StateChange;
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
		assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+elementName(activity)+" transition "+transition.getName().toString()+" for IO set "+ioSet);	
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testDataAssociationsBetweenParallelWritesAreCreated(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
		assumeTrue(ioSet.writes(first) && ioSet.writes(second), "Activity does not write both data objects.");
		assumeFalse(ioSet.associationShouldAlreadyBeInPlace(first, second), "Activity reads both data objects in the same collection/non-collection way as they are written, so an association is already in place");
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		assertEquals(1, arcsToNodeNamed(transition, "associations").filter(writesAssociation(first+"Id", second+"Id")).count(),
				"There is not exactly one writing association arc for objects "+first+" and "+second+" at activity "+elementName(activity)+" transition "+transition.getName().toString());
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testAssociationsAreCheckedWhenReading(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
		assumeTrue(ioSet.reads(first) && ioSet.reads(second), "Activity does not read both data objects.");
		
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		assertTrue(hasGuardForAssociation(transition, first, second),
				"There is no guard for association when reading objects "+first+" and "+second+" at activity "+elementName(activity)+" transition "+transition.getName().toString());

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
	/** Lower bounds must not be checked if associated objects are read together, as they should already have been checked when the single object was created*/
	public void testLowerLimitsAreNotCheckedWhenReadTogetherWithCollection(String first, String second, Activity activity, DataObjectIdIOSet ioSet) {
		Association assoc = dataModel.getAssociation(first, second).get();
		int lowerBound = assoc.getEnd(second).getLowerBound();
		assumeTrue(lowerBound > 1, "No lower bound that has to be checked");
		assumeTrue(ioSet.reads(first) && ioSet.reads(second), "no "+first+" is read together with "+second);
		//TODO is there a test case that fulfills the assumptions?
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		assertEquals(0, guardsOf(transition).filter(guard -> isDirectLowerBoundGuard(guard, first, second, lowerBound)).count(),
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
		assertEquals(1, guardsOf(transition).filter(guard -> isLowerBoundGuardViaCollectionIdentifier(guard, first, second, lowerBound, ioSet)).count(),
				"There is not exactly one guard for lower limit "+lowerBound+" of assoc "+first+"-"+second+" at activity \""+elementName(activity)+"\"");
	}
	
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	/** Goal lower bounds must be checked when a data object changes to a state where the number of associations cannot change anymore*/
	public void testGoalCardinalitiesAreCheckedOnStateChange(Activity activity,  DataObjectIdIOSet ioSet) {
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();

		dataObjectStateChangesWithRemovedUpdateableAssociations(ioSet).forEach(x -> {
			StateChange stateChange = x.first;
			AssociationEnd removedAssociationEnd = x.second;
			String dataObjectName = stateChange.first.dataElementName();
			String otherDataObject = removedAssociationEnd.getDataObject();
			
			boolean assocIsCreated = ioSet.creates(dataObjectName) || ioSet.creates(otherDataObject);
			if(removedAssociationEnd.hasTightGoalLowerBound(assocIsCreated)) {
				int goalLowerBound = removedAssociationEnd.getGoalLowerBound(assocIsCreated);
				assertEquals(1, guardsOf(transition).filter(guard -> 
				(isDirectLowerBoundGuard(guard, dataObjectName, otherDataObject, goalLowerBound))
				&& guard.contains(IOSetCompiler.GOAL_CARDINALITY_COMMENT)).count(), 
					"Activity transition "+transition.getName().asString()+" for io set "+ioSet+" does not check for goal lower bound between "+dataObjectName+" and "+otherDataObject
					+" although "+dataObjectName+" changes state from "+stateChange.first.getStateName()+" to "+stateChange.second.getStateName()+" where no new associations can be created");
			}
		});
	}
	
	public Stream<Pair<StateChange, AssociationEnd>> dataObjectStateChangesWithRemovedUpdateableAssociations(DataObjectIdIOSet ioSet) {
		return ioSet.stateChanges().stream().flatMap(stateChange -> {
			ObjectLifeCycle olc = olcFor(stateChange.first.dataElementName());
			Set<AssociationEnd> removedUpdateableAssociations = new HashSet<>(olc.getState(stateChange.first.getStateName()).get().getUpdateableAssociations());
			removedUpdateableAssociations.removeAll(olc.getState(stateChange.second.getStateName()).get().getUpdateableAssociations());
			return removedUpdateableAssociations.stream().map(removedAssoc -> new Pair<>(stateChange, removedAssoc));
		});
	}

	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testCheckedGoalCardinalitiesComeFromStateChange(Activity activity,  DataObjectIdIOSet ioSet) {
		Transition transition = transitionForIoCombination(ioAssociationsToStateMaps(ioSet), activity).get();
		
		guardsOf(transition).filter(guard -> guard.contains(IOSetCompiler.GOAL_CARDINALITY_COMMENT)).forEach(guard -> {
			guard = removeComments(guard);
			assertTrue(guard.matches("\\(enforceLowerBound .+Id .+ assoc .+\\)"), 
					"Guard \""+guard+"\" annotated with goal cardinality comment was not a lower bound check");
			
			guard = guard.replace("(enforceLowerBound ", "");
			String first = guard.split("Id ")[0];
			guard = guard.split("Id ")[1];
			String second = guard.split(" assoc ")[0];

			assertTrue(dataObjectStateChangesWithRemovedUpdateableAssociations(ioSet).anyMatch(x -> {
				StateChange stateChange = x.first;
				AssociationEnd removedAssociationEnd = x.second;
				return (stateChange.first.dataElementName().equals(first) && removedAssociationEnd.getDataObject().equals(second));
			}), "No updateable association that is removed on state change matches goal lower bound cardinality guard \""+guard+"\n "
					+ "of transition "+transition.getName().asString()+" of activity "+elementName(activity));
		});
	}
	
}
