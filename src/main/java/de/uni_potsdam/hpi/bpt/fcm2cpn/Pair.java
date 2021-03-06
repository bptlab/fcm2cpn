package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.Objects;

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
	
	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if(getClass() != obj.getClass()) {
			Pair<?, ?> other = (Pair<?, ?>) obj;
			if(Objects.equals(first, other.first) && Objects.equals(second, other.second)) throw new Error("Not possible");
		}
		if (getClass() != obj.getClass()) return false;
		Pair<?, ?> other = (Pair<?, ?>) obj;
		return Objects.equals(first, other.first) && Objects.equals(second, other.second);
	}
	
}
