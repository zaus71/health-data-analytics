package org.snomed.heathanalytics.server.service.pathlingclient;

import java.util.ArrayList;
import java.util.List;

public class AggregateResponse {

	private String resourceType;

	private List<Parameter> parameter;

	public AggregateResponse() {
	}

	public int getGroupingCount(boolean... groupMatch) {
		for (Parameter parameter : parameter) {
			if ("grouping".equals(parameter.getName())) {
				List<Boolean> matchSequence = new ArrayList<>();
				int result = 0;
				for (Parameter part : parameter.getPart()) {
					if ("label".equals(part.getName()) && part.getValueBoolean() != null) {
						matchSequence.add(part.getValueBoolean());
					}
					if ("result".equals(part.getName()) && part.getValueUnsignedInt() != null) {
						result = part.getValueUnsignedInt();
					}
				}
				if (matchSequence.size() == groupMatch.length) {
					boolean sequenceMatch = true;
					for (int i = 0; i < groupMatch.length; i++) {
						if (matchSequence.get(i) != groupMatch[i]) {
							sequenceMatch = false;
							break;
						}
					}
					if (sequenceMatch) {
						return result;
					}
				}
			}
		}
		return 0;
	}

	public String getResourceType() {
		return resourceType;
	}

	public void setResourceType(String resourceType) {
		this.resourceType = resourceType;
	}

	public List<Parameter> getParameter() {
		return parameter;
	}

	public void setParameter(List<Parameter> parameter) {
		this.parameter = parameter;
	}
}
