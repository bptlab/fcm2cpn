package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ObjectLifeCycle {
    private final String className;
    private final Set<State> states;

    public ObjectLifeCycle(String className) {
        this.states = new HashSet<>();
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
    

    public static class State {
        protected String stateName;
        protected Set<State> successors;
        protected Set<AssociationEnd> updateableAssociations;

        public State(String stateName) {
            this.stateName = stateName;
            this.successors = new HashSet<>();
            this.updateableAssociations = new HashSet<>();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof State &&
            stateName.equals(((State)obj).stateName);
        }

        public void addSuccessor(State successor) {
            successors.add(successor);
        }

        public String getStateName() {
            return stateName;
        }

        public void setStateName(String stateName) {
            this.stateName = stateName;
        }

        public Set<State> getSuccessors() {
            return successors;
        }

        public void setSuccessors(Set<State> successors) {
            this.successors = successors;
        }

        public Set<AssociationEnd> getUpdateableAssociations() {
            return updateableAssociations;
        }

        public void setUpdateableAssociations(Set<AssociationEnd> updateableAssociations) {
            this.updateableAssociations = updateableAssociations;
        }

        @Override
        public int hashCode() {
            return stateName.hashCode();
        }
        
        @Override
        public String toString() {
        	return "State("+stateName+", -> "+successors.stream().map(State::getStateName).collect(Collectors.toList())+")";
        }
    }
}
