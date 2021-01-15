package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle.State;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycleParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.AssociationsProvider;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class ObjectLifeCycleParserTests extends ModelStructureTests {
	
	private ObjectLifeCycle[] olcs;
	@Override
	public void compileModel() {
		BpmnPreprocessor.process(bpmn);
		parseDataModel();
		this.olcs = ObjectLifeCycleParser.getOLCs(dataModel, bpmn);
	}    
	
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testOLCsAreComplete(DataObject dataObject) {
		String className = normalizeElementName(dataObject.getName());
		assertNotNull(olcFor(className), "No OLC for "+className+" exists.");
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testAllTransitionsByActivityArePossible(Activity activity, DataObjectIOSet ioSet) {
		Pair<Map<String, Optional<String>>, Map<String, Optional<String>>> ioCombination = ioAssociationsToStateMaps(ioSet);
		ioCombination.first.forEach((object, inputState) -> {
			Optional<String> outputState = ioCombination.second.get(object);// All read objects are written back, so this is never null
			if(!inputState.equals(outputState)) {
				ObjectLifeCycle olc = olcFor(object);
				assertTrue(olc.getState(inputState.get()).get().getSuccessors().contains(olc.getState(outputState.get()).get()), 
						"Olc does not support lifecycle transition ("+inputState.get()+" -> "+outputState.get()+") for data object "+object+" which is definied by activity "+activity.getName());
			}
		});
	}
	
	@TestWithAllModels
	public void testAllOLCStateTransitionsAreValid() {
		forEachOLCTransition((olc, states) -> {
			String dataObject = olc.getClassName();
			boolean anyActivityCanTakeThisTransition = bpmn.getModelElementsByType(Activity.class).stream().anyMatch(activity -> {
				return ioAssociationCombinations(activity).stream().anyMatch(ioSet -> 
				ioSet.first.stream().anyMatch(inputAssoc -> inputAssoc.dataElementName().equals(dataObject) && states.first.getStateName().equals(inputAssoc.getStateName().orElse(null)))
				&& ioSet.second.stream().anyMatch(outputAssoc -> outputAssoc.dataElementName().equals(dataObject) && states.second.getStateName().equals(outputAssoc.getStateName().orElse(null)))
				);
			});
			assertTrue(anyActivityCanTakeThisTransition, "There is no activity which changes state of '"+dataObject+"' from "+states.first.getStateName()+" to "+states.second.getStateName());
		});
	}
	

	@Test
	@ModelsToTest("NoIOSpecification")
	public void testOLCIsCreatedWithoutIOSpecification() {
		ObjectLifeCycle olc = olcFor(normalizeElementName("A"));
		assertEquals(new HashSet<>(Arrays.asList(olc.getState("Y").get())), olc.getState("X").get().getSuccessors(),
				"OLC Transition for activity without IO Specification was not created.");
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testEachCreationLeadsToUpdateableAssociations(String first, String second, Activity activity) {
		Association assoc = dataModel.getAssociation(first, second).get();
		AssociationEnd relevantEnd = assoc.getEnd(second);
		assumeTrue(relevantEnd.getGoalLowerBound() > relevantEnd.getLowerBound(), second+" has no tight goal lower bound towards "+first);
		
		ioAssociationCombinations(activity).stream()
			.filter(ioCombination -> createsAssociationBetween(ioCombination, first, second))
			.forEach(ioCombination -> {
				var stateMap = ioAssociationsToStateMaps(ioCombination);
				Optional<String> readStateName = stateMap.first.get(first);
				if(readStateName.isPresent()) {
					State readState = olcFor(first).getState(readStateName.get()).get();
					assertTrue(readState.getUpdateableAssociations().contains(relevantEnd), 
							"Association between "+first+" and "+second+" is created in one IO-Set of activity "+elementName(activity)
									+ ", but not marked in state "+readState.getStateName()+" of "+first);
				}
			});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testEachUpdateableAssociationsComesFromCreation(DataObject dataObject) {
		String dataObjectName = normalizeElementName(elementName((ItemAwareElement)dataObject));
		ObjectLifeCycle olc = olcFor(dataObjectName);
		Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
		
		olc.getStates().forEach(state -> {
			state.getUpdateableAssociations().forEach(relevantEnd -> {
				String otherDataObject = relevantEnd.getDataObject();
				assertTrue(relevantEnd.getGoalLowerBound() > relevantEnd.getLowerBound(), 
						otherDataObject+" has no tight goal lower bound towards "+dataObjectName+" but the latter has an updateable association to it.");
				assertTrue(activities.stream()
					.flatMap(activity -> ioAssociationCombinations(activity).stream())
					.filter(ioCombination -> createsAssociationBetween(ioCombination, dataObjectName, otherDataObject))
					.map(ObjectLifeCycleParserTests::ioAssociationsToStateMaps)
					.anyMatch(stateMap -> olc.getState(stateMap.first.get(dataObjectName).get()).get().equals(state)),
					
					dataObjectName+" state "+state+" has an updateable association to "+otherDataObject
					+", but there is no IO combination of any activity that creates an association while "+dataObjectName+" is in that state");
			});
		});		
	}
	
	
//=========== Utility ================
	
	public ObjectLifeCycle olcFor(String dataObject) {
    	return Arrays.stream(olcs)
    			.filter(olc -> olc.getClassName().equals(dataObject))
    			.findAny()
    			.get();
    }
	
	public void forEachOLCTransition(BiConsumer<ObjectLifeCycle, Pair<ObjectLifeCycle.State, ObjectLifeCycle.State>> consumer) {
		for(ObjectLifeCycle olc : olcs) {
			for(ObjectLifeCycle.State startState : olc.getStates()) {
				for(ObjectLifeCycle.State targetState : startState.getSuccessors()) {
					consumer.accept(olc, new Pair<>(startState, targetState));
				}
			}
		}
	}
	

}
