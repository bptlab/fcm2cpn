package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.assumeNameIsNormalized;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ObjectLifeCycle {
    private final String className;
    private final Set<State> states;

    public ObjectLifeCycle(String className) {
        this.states = new HashSet<>();
        assumeNameIsNormalized(className);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public Set<State> getStates() {
        return states;
    }

    public Optional<State> getState(String inputState) {
        return states.stream().filter(state -> state.stateName.equals(inputState)).findAny();
    }
    
    public void addState(String stateName) {
    	states.add(new State(stateName));
    }
    

    public class State {
        protected final String stateName;
        protected final Set<State> successors;
        /**Association where the cardinality can still change when a data object is in this state*/
        protected final Set<AssociationEnd> updateableAssociations;

        private State(String stateName) {
            this.stateName = stateName;
            this.successors = new HashSet<>();
            this.updateableAssociations = new HashSet<>();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof State &&
            stateName.equals(((State)obj).stateName);
        }
        
        public String getStateName() {
            return stateName;
        }
        
        public void addSuccessor(State successor) {
            successors.add(successor);
        }

        public Set<State> getSuccessors() {
            return successors;
        }
        
        public void addUpdateableAssociation(AssociationEnd updateableAssociation) {
        	updateableAssociations.add(updateableAssociation);
        }

        public Set<AssociationEnd> getUpdateableAssociations() {
            return updateableAssociations;
        }


        @Override
        public int hashCode() {
            return stateName.hashCode();
        }
        
        @Override
        public String toString() {
        	return "State("+stateName+", -> "+successors.stream().map(State::getStateName).collect(Collectors.toList())+")";
        }
        
        public ObjectLifeCycle getOLC() {
        	return ObjectLifeCycle.this;
        }
    }
}
