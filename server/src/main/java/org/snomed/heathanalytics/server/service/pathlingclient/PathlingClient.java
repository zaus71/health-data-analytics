package org.snomed.heathanalytics.server.service.pathlingclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import org.snomed.heathanalytics.server.service.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PathlingClient {

	private final RestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	public PathlingClient(@Value("${pathling.api.url}") String pathlingUrl) {
		restTemplate = new RestTemplateBuilder().rootUri(pathlingUrl).build();
	}

	public int getPatientCount() throws ServiceException {
		ResponseEntity<ResourceList> responseEntity = restTemplate.exchange(
				UriComponentsBuilder.fromPath("/Patient/_search")
						.queryParam("_query", "fhirPath")
						.queryParam("_format", "json")
						.queryParam("_count", 0)
				.toUriString(), HttpMethod.GET, null, ResourceList.class);
		ResourceList patientList = responseEntity.getBody();
		if (patientList == null) {
			throw new ServiceException(String.format("Failed to fetch patient count from Pathling. No response body. Response code %s.", responseEntity.getStatusCode()));
		}
		return patientList.getTotal();
	}

	public AggregateResponse aggregate(AggregateRequest searchRequest) throws ServiceException {
		String body;
		try {
			body = objectMapper.writeValueAsString(searchRequest);
		} catch (JsonProcessingException e) {
			throw new ServiceException("Failed to serialise aggregate request.", e);
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json"));
		ResponseEntity<AggregateResponse> responseEntity = restTemplate.exchange("/$aggregate", HttpMethod.POST, new HttpEntity<>(body, headers), AggregateResponse.class);
		AggregateResponse aggregateResponse = responseEntity.getBody();
		if (aggregateResponse == null) {
			throw new ServiceException(String.format("Failed to perform aggregate function on Pathling. No response body. Response code %s.", responseEntity.getStatusCode()));
		}
		return aggregateResponse;
	}
}
