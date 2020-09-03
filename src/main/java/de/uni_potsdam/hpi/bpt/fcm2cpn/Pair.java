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
		final int prime = 5;
		int result = 1;
		result = prime * result + Objects.hash(first, second);
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Pair<?, ?> other = (Pair<?, ?>) obj;
		return Objects.equals(first, other.first) && Objects.equals(second, other.second);
	}
	
}
