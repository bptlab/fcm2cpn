package de.uni_potsdam.hpi.bpt.fcm2cpn;

public class Pair<S,T> {
	
	public final S first;
	public final T second;

	public Pair(S first, T second) {
		this.first = first;
		this.second = second;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName()+"("+first+", "+second+")";
	}
	
}
