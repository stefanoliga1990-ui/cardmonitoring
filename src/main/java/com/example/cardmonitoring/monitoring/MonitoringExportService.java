package com.example.cardmonitoring.monitoring;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.pricing.ConfidenceLevel;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class MonitoringExportService {

	private static final DateTimeFormatter FILE_TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
	private static final String CSV_HEADER = String.join(",",
			"monitoraggio_id",
			"carta",
			"set",
			"lingua",
			"condizione",
			"prima_edizione",
			"reverse",
			"gradata",
			"casa_grading",
			"voto_grading",
			"firmata",
			"alterata",
			"valuta",
			"prezzo_acquisto_cents",
			"data_calcolo",
			"prezzo_medio_cents",
			"prezzo_minimo_cents",
			"prezzo_massimo_cents",
			"offerte_compatibili",
			"offerte_usate",
			"attendibilita");

	private final MonitoringRepository monitoringRepository;
	private final PriceObservationRepository observationRepository;
	private final ObjectMapper objectMapper;

	public MonitoringExportService(
			MonitoringRepository monitoringRepository,
			PriceObservationRepository observationRepository,
			ObjectMapper objectMapper) {
		this.monitoringRepository = monitoringRepository;
		this.observationRepository = observationRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public ExportedFile export(long ownerId, ExportFormat format) {
		Instant exportedAt = Instant.now();
		List<Monitoring> monitorings = monitoringRepository.findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(ownerId);
		Map<Long, List<PriceObservation>> observationsByMonitoring = observationsByMonitoring(monitorings);
		List<MonitoringExport> exports = monitorings.stream()
				.map((monitoring) -> MonitoringExport.from(
						monitoring,
						observationsByMonitoring.getOrDefault(monitoring.getId(), List.of())))
				.toList();

		return switch (format) {
			case CSV -> csv(exportedAt, exports);
			case JSON -> json(exportedAt, exports);
		};
	}

	private Map<Long, List<PriceObservation>> observationsByMonitoring(List<Monitoring> monitorings) {
		if (monitorings.isEmpty()) {
			return Map.of();
		}
		List<Long> monitoringIds = monitorings.stream()
				.map(Monitoring::getId)
				.toList();
		Map<Long, List<PriceObservation>> observationsByMonitoring = new LinkedHashMap<>();
		for (PriceObservation observation : observationRepository
				.findByMonitoringIdsOrderByMonitoringIdAndObservedAt(monitoringIds)) {
			observationsByMonitoring
					.computeIfAbsent(observation.getMonitoring().getId(), ignored -> new ArrayList<>())
					.add(observation);
		}
		return observationsByMonitoring;
	}

	private ExportedFile csv(Instant exportedAt, List<MonitoringExport> monitorings) {
		StringBuilder csv = new StringBuilder();
		csv.append('\ufeff');
		csv.append(CSV_HEADER).append('\n');
		for (MonitoringExport monitoring : monitorings) {
			if (monitoring.observations().isEmpty()) {
				appendCsvRow(csv, monitoring, null);
			}
			for (ObservationExport observation : monitoring.observations()) {
				appendCsvRow(csv, monitoring, observation);
			}
		}
		return new ExportedFile(
				"card-monitor-export-" + FILE_TIMESTAMP.format(exportedAt) + ".csv",
				"text/csv;charset=UTF-8",
				csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void appendCsvRow(StringBuilder csv, MonitoringExport monitoring, ObservationExport observation) {
		List<String> values = new ArrayList<>();
		values.add(String.valueOf(monitoring.id()));
		values.add(monitoring.cardLabel());
		values.add(monitoring.expansionName());
		values.add(monitoring.criteria().language());
		values.add(value(monitoring.criteria().condition()));
		values.add(String.valueOf(monitoring.criteria().firstEdition()));
		values.add(String.valueOf(monitoring.criteria().reverse()));
		values.add(String.valueOf(monitoring.criteria().graded()));
		values.add(value(monitoring.criteria().gradingCompany()));
		values.add(value(monitoring.criteria().gradingGrade()));
		values.add(String.valueOf(monitoring.criteria().signed()));
		values.add(String.valueOf(monitoring.criteria().altered()));
		values.add(monitoring.currency());
		values.add(value(monitoring.purchasePriceCents()));
		values.add(observation == null ? "" : observation.observedAt().toString());
		values.add(observation == null ? "" : value(observation.averagePriceCents()));
		values.add(observation == null ? "" : value(observation.minimumPriceCents()));
		values.add(observation == null ? "" : value(observation.maximumPriceCents()));
		values.add(observation == null ? "" : String.valueOf(observation.compatibleOffers()));
		values.add(observation == null ? "" : String.valueOf(observation.usedOffers()));
		values.add(observation == null ? "" : observation.confidence().name());
		csv.append(String.join(",", values.stream().map(MonitoringExportService::csvValue).toList()))
				.append('\n');
	}

	private ExportedFile json(Instant exportedAt, List<MonitoringExport> monitorings) {
		try {
			byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
					.writeValueAsBytes(new DashboardExport(exportedAt, monitorings));
			return new ExportedFile(
					"card-monitor-export-" + FILE_TIMESTAMP.format(exportedAt) + ".json",
					"application/json;charset=UTF-8",
					content);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Unable to serialize monitoring export", exception);
		}
	}

	private static String csvValue(String value) {
		String safeValue = value == null ? "" : value;
		return "\"" + safeValue.replace("\"", "\"\"") + "\"";
	}

	private static String value(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	public enum ExportFormat {
		CSV,
		JSON;

		static ExportFormat from(String value) {
			for (ExportFormat format : values()) {
				if (format.name().equalsIgnoreCase(value)) {
					return format;
				}
			}
			throw new IllegalArgumentException("Unsupported export format");
		}
	}

	public record ExportedFile(String filename, String contentType, byte[] content) {
	}

	public record DashboardExport(Instant exportedAt, List<MonitoringExport> monitorings) {
	}

	public record MonitoringExport(
			long id,
			long blueprintId,
			long expansionId,
			String cardName,
			String cardVersion,
			String cardLabel,
			String expansionName,
			String expansionCode,
			CriteriaExport criteria,
			String currency,
			Long purchasePriceCents,
			Instant createdAt,
			Instant lastCheckedAt,
			String lastError,
			List<ObservationExport> observations) {

		static MonitoringExport from(Monitoring monitoring, List<PriceObservation> observations) {
			return new MonitoringExport(
					monitoring.getId(),
					monitoring.getBlueprintId(),
					monitoring.getExpansionId(),
					monitoring.getCardName(),
					monitoring.getCardVersion(),
					monitoring.getCardName() + " (" + monitoring.getCardVersion() + ")",
					monitoring.getExpansionName(),
					monitoring.getExpansionCode(),
					CriteriaExport.from(monitoring),
					monitoring.getCurrency(),
					monitoring.getPurchasePriceCents(),
					monitoring.getCreatedAt(),
					monitoring.getLastCheckedAt(),
					monitoring.getLastError(),
					observations.stream().map(ObservationExport::from).toList());
		}
	}

	public record CriteriaExport(
			String language,
			String condition,
			boolean firstEdition,
			boolean reverse,
			boolean graded,
			String gradingCompany,
			String gradingGrade,
			boolean signed,
			boolean altered) {

		static CriteriaExport from(Monitoring monitoring) {
			return new CriteriaExport(
					monitoring.getLanguage(),
					monitoring.getCondition(),
					monitoring.isFirstEdition(),
					monitoring.isReverse(),
					monitoring.isGraded(),
					monitoring.getGradingCompany(),
					monitoring.getGradingGrade(),
					monitoring.isSigned(),
					monitoring.isAltered());
		}
	}

	public record ObservationExport(
			Instant observedAt,
			String currency,
			BigDecimal averagePriceCents,
			Long minimumPriceCents,
			Long maximumPriceCents,
			int compatibleOffers,
			int usedOffers,
			ConfidenceLevel confidence) {

		static ObservationExport from(PriceObservation observation) {
			return new ObservationExport(
					observation.getObservedAt(),
					observation.getCurrency(),
					observation.getAveragePriceCents(),
					observation.getMinimumPriceCents(),
					observation.getMaximumPriceCents(),
					observation.getCompatibleOffers(),
					observation.getUsedOffers(),
					observation.getConfidence());
		}
	}
}
