package org.openjdk.jmc.flightrecorder.flameview.views;

import java.util.List;
import java.util.stream.Collectors;

class JSON {

	private static String addQuotes(String str) {
		return "\"" + str + "\"";
	}

	public String marshal(List<String> items) {
		return "[" + items.stream().map(JSON::addQuotes).collect(Collectors.joining(", ")) + "]";
	}
}
