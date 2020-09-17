package org.snomed.heathanalytics.server.service;

import org.slf4j.LoggerFactory;
import org.snomed.heathanalytics.model.Patient;
import org.snomed.heathanalytics.server.model.*;
import org.snomed.heathanalytics.server.service.pathlingclient.AggregateResponse;
import org.snomed.heathanalytics.server.service.pathlingclient.PathlingClient;
import org.snomed.heathanalytics.server.service.pathlingclient.AggregateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReportService {

	@Autowired
	private QueryService queryService;

	@Autowired
	private PathlingClient pathlingClient;

	public Report runReport(ReportDefinition reportDefinition) throws ServiceException {
		Timer timer = new Timer();
		// Fetch page of patients matching top level criteria
		CohortCriteria patientCriteria = reportDefinition.getCriteria();
		int count;
		if (patientCriteria != null) {
			count = queryService.fetchCohortCount(patientCriteria);
		} else {
			count = (int) queryService.getStats().getPatientCount();
		}
		timer.split("cohort count");
		Report report = new Report(reportDefinition.getName(), count, patientCriteria);

		List<List<SubReportDefinition>> subGroupLists = reportDefinition.getGroups();
		addReportGroups(report, subGroupLists, 0, patientCriteria, timer);

		LoggerFactory.getLogger(getClass()).info("Times: {}", timer.getTimes());
		return report;
	}

	public StatisticalCorrelationReport runStatisticalReport(StatisticalCorrelationReportDefinition reportDefinition) throws ServiceException {

		if (reportDefinition.isUsePathlingAPI()) {
			// Call the Pathling API

			int patientCount = pathlingClient.getPatientCount();

			AggregateResponse aggregateResponse = pathlingClient.aggregate(new AggregateRequest("Patient")
					.addAggregation("Number of patients", "count()")

					// Treatment
					.addGrouping("Prescribed TNF inhibitor", "reverseResolve(MedicationRequest.subject).medicationCodeableConcept.coding contains http://snomed.info/sct|416897008")
					// Negative outcome
					.addGrouping("Contracted lung infection", "reverseResolve(Condition.subject).code.memberOf('http://snomed.info/sct?fhir_vs=ecl/<<53084003') contains true")

					// COPD
					.addFilter("reverseResolve(Condition.subject).code.memberOf('http://snomed.info/sct?fhir_vs=ecl/<<13645005') contains true")
					// RA
					.addFilter("reverseResolve(Condition.subject).code.memberOf('http://snomed.info/sct?fhir_vs=ecl/<<69896004') contains true")
			);

			int withTreatmentWithNegativeOutcomeCount = aggregateResponse.getGroupingCount(true, true);
			int withTreatmentWithoutNegativeOutcomeCount = aggregateResponse.getGroupingCount(true, false);
			int withTreatmentCount = withTreatmentWithNegativeOutcomeCount + withTreatmentWithoutNegativeOutcomeCount;

			int withoutTreatmentWithNegativeOutcomeCount = aggregateResponse.getGroupingCount(false, true);
			int withoutTreatmentWithoutNegativeOutcomeCount = aggregateResponse.getGroupingCount(false, false);
			int withoutTreatmentCount = withoutTreatmentWithNegativeOutcomeCount + withoutTreatmentWithoutNegativeOutcomeCount;

			return new StatisticalCorrelationReport(
					patientCount,
					withTreatmentCount,
					withTreatmentWithNegativeOutcomeCount,
					withoutTreatmentCount,
					withoutTreatmentWithNegativeOutcomeCount);

		} else {
			// Use Elasticsearch

			CohortCriteria patientCriteria = new CohortCriteria();

			// Copy base criteria
			patientCriteria.copyCriteriaWhereMoreSpecific(reportDefinition.getBaseCriteria());

			EncounterCriterion treatmentCriterion = reportDefinition.getTreatmentCriterion();
			InputValidationHelper.checkInput("treatmentCriterion is required for the statistical test.", treatmentCriterion != null);
			EncounterCriterion negativeOutcomeCriterion = reportDefinition.getNegativeOutcomeCriterion();
			InputValidationHelper.checkInput("negativeOutcomeCriterion is required for a statistical test.", negativeOutcomeCriterion != null);

			// A. Count patients WITH treatment, WITH negative outcome
			List<EncounterCriterion> encounterCriteria = patientCriteria.getEncounterCriteria();
			encounterCriteria.add(treatmentCriterion);
			encounterCriteria.add(negativeOutcomeCriterion);
			int withTreatmentWithNegativeOutcomeCount = queryService.fetchCohortCount(patientCriteria);

			// B. Count patients WITH treatment
			removeLast(encounterCriteria);
			int withTreatmentCount = queryService.fetchCohortCount(patientCriteria);

			// Has test variable chance of outcome = A / B

			// C. Count patients WITHOUT treatment, WITH negative outcome
			treatmentCriterion.setHas(false);
			encounterCriteria.add(negativeOutcomeCriterion);
			int withoutTreatmentWithNegativeOutcomeCount = queryService.fetchCohortCount(patientCriteria);

			// D. Count patients WITHOUT test variable
			removeLast(encounterCriteria);
			int withoutTreatmentCount = queryService.fetchCohortCount(patientCriteria);
			treatmentCriterion.setHas(true);// reset

			// Has not test variable chance of outcome = C / D

			return new StatisticalCorrelationReport(
					(int) queryService.getStats().getPatientCount(),
					withTreatmentCount,
					withTreatmentWithNegativeOutcomeCount,
					withoutTreatmentCount,
					withoutTreatmentWithNegativeOutcomeCount);
		}
	}

	private void addReportGroups(Report report, List<List<SubReportDefinition>> groupLists, int listsIndex, CohortCriteria patientCriteria, Timer timer) throws ServiceException {
		if (groupLists != null && groupLists.size() > listsIndex) {
			List<SubReportDefinition> groupList = groupLists.get(listsIndex);

			// Clear CPT Analysis flag of inherited criterion to allow a report focused on the most specific criteria
			if (patientCriteria != null) {
				patientCriteria = patientCriteria.clone();
				patientCriteria.getEncounterCriteria().forEach(encounterCriterion -> encounterCriterion.setIncludeCPTAnalysis(false));
			}

			for (SubReportDefinition reportDefinition : groupList) {
				CohortCriteria combinedCriteria = combineCriteria(patientCriteria, reportDefinition.getCriteria());
				Page<Patient> patientsPage = queryService.fetchCohort(combinedCriteria);
				timer.split("Fetch for " + reportDefinition.getName());
				Map<String, CPTTotals> cptTotals = null;
				if (patientsPage instanceof PatientPageWithCPTTotals) {
					PatientPageWithCPTTotals pageWithEncounterCounts = (PatientPageWithCPTTotals) patientsPage;
					cptTotals = pageWithEncounterCounts.getCptTotals();
				}
				Report reportGroup = new Report(reportDefinition.getName(), (int) patientsPage.getTotalElements(), combinedCriteria, cptTotals);
				report.addGroup(reportGroup);
				addReportGroups(reportGroup, groupLists, listsIndex + 1, combinedCriteria, timer);
			}
		}
	}

	private CohortCriteria combineCriteria(CohortCriteria mainCriteria, CohortCriteria additionalCriteria) {
		CohortCriteria combinedCriteria = new CohortCriteria();
		combinedCriteria.copyCriteriaWhereMoreSpecific(mainCriteria);
		combinedCriteria.copyCriteriaWhereMoreSpecific(additionalCriteria);
		return combinedCriteria;
	}

	private void removeLast(List<EncounterCriterion> list) {
		list.remove(list.size() - 1);
	}

}
