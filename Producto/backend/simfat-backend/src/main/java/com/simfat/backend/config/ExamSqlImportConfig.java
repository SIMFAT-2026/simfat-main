package com.simfat.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.AlertRuleRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExamSqlImportConfig {

    private static final Pattern REGION_PATTERN = Pattern.compile(
        "INSERT\\s+INTO\\s+region\\s+VALUES\\s*\\((\\d+),'([^']+)'\\);",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGION_BASE_PATTERN = Pattern.compile(
        "INSERT\\s+INTO\\s+region_base\\s+VALUES\\s*\\((\\d+),\\s*([0-9]+(?:\\.[0-9]+)?),\\s*([0-9]+(?:\\.[0-9]+)?),\\s*([0-9]+(?:\\.[0-9]+)?)\\);",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LOSS_PATTERN = Pattern.compile(
        "INSERT\\s+INTO\\s+perdida_anual\\s+VALUES\\s*\\((\\d+),\\s*(\\d{4}),\\s*([0-9]+(?:\\.[0-9]+)?),\\s*(NULL|'[^']*')\\);",
        Pattern.CASE_INSENSITIVE
    );

    @Bean
    @ConditionalOnProperty(name = "app.seed.exam.enabled", havingValue = "true")
    CommandLineRunner importExamSqlData(
        RegionRepository regionRepository,
        ForestLossRecordRepository forestLossRepository,
        HeatAlertEventRepository heatAlertEventRepository,
        AlertRuleRepository alertRuleRepository,
        ObjectMapper objectMapper,
        @Value("${app.seed.exam.sql-path}") String sqlPathValue
    ) {
        return args -> {
            Path sqlPath = Paths.get(sqlPathValue);
            if (!Files.exists(sqlPath)) {
                throw new IllegalStateException("No se encontro el archivo SQL: " + sqlPath.toAbsolutePath());
            }

            Map<Integer, String> regionNamesByNumericId = new LinkedHashMap<>();
            Map<Integer, Double> reference2000ByRegionId = new HashMap<>();
            List<LossRow> lossRows = new ArrayList<>();

            List<String> lines = Files.readAllLines(sqlPath, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.trim();

                Matcher regionMatcher = REGION_PATTERN.matcher(line);
                if (regionMatcher.find()) {
                    Integer regionNumericId = Integer.parseInt(regionMatcher.group(1));
                    String rawRegionName = regionMatcher.group(2);
                    regionNamesByNumericId.put(regionNumericId, normalizeMojibake(rawRegionName));
                    continue;
                }

                Matcher regionBaseMatcher = REGION_BASE_PATTERN.matcher(line);
                if (regionBaseMatcher.find()) {
                    Integer regionNumericId = Integer.parseInt(regionBaseMatcher.group(1));
                    Double extent2000 = Double.parseDouble(regionBaseMatcher.group(2));
                    reference2000ByRegionId.put(regionNumericId, extent2000);
                    continue;
                }

                Matcher lossMatcher = LOSS_PATTERN.matcher(line);
                if (lossMatcher.find()) {
                    Integer regionNumericId = Integer.parseInt(lossMatcher.group(1));
                    Integer year = Integer.parseInt(lossMatcher.group(2));
                    Double hectaresLost = Double.parseDouble(lossMatcher.group(3));
                    lossRows.add(new LossRow(regionNumericId, year, hectaresLost));
                }
            }

            if (regionNamesByNumericId.isEmpty() || reference2000ByRegionId.isEmpty() || lossRows.isEmpty()) {
                throw new IllegalStateException("El SQL no contenia datos suficientes para importar.");
            }

            Path backupDir = createJsonBackup(regionRepository, forestLossRepository, heatAlertEventRepository, alertRuleRepository, objectMapper);

            forestLossRepository.deleteAll();
            heatAlertEventRepository.deleteAll();
            alertRuleRepository.deleteAll();
            regionRepository.deleteAll();

            List<Region> regionsToInsert = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : regionNamesByNumericId.entrySet()) {
                Integer numericId = entry.getKey();
                Double reference = reference2000ByRegionId.getOrDefault(numericId, 0D);

                Region region = new Region();
                region.setNombre(entry.getValue());
                region.setCodigo("CL-" + numericId);
                region.setZona(inferZoneByRegionId(numericId));
                region.setHectareasBosqueReferencia(reference > 0 ? reference : 1D);
                regionsToInsert.add(region);
            }

            List<Region> savedRegions = regionRepository.saveAll(regionsToInsert);
            Map<Integer, String> mongoRegionIdByNumericId = new HashMap<>();
            for (Region region : savedRegions) {
                Integer numericId = Integer.valueOf(region.getCodigo().replace("CL-", ""));
                mongoRegionIdByNumericId.put(numericId, region.getId());
            }

            List<ForestLossRecord> records = new ArrayList<>();
            for (LossRow row : lossRows) {
                String mongoRegionId = mongoRegionIdByNumericId.get(row.regionNumericId());
                Double reference = reference2000ByRegionId.get(row.regionNumericId());

                if (mongoRegionId == null || reference == null || reference <= 0) {
                    continue;
                }

                ForestLossRecord record = new ForestLossRecord();
                record.setRegionId(mongoRegionId);
                record.setAnio(row.year());
                record.setHectareasPerdidas(row.hectaresLost());
                record.setPorcentajePerdida((row.hectaresLost() / reference) * 100D);
                record.setFuente("Script Examen SQL");
                record.setFechaRegistro(LocalDateTime.now().minusDays(1));
                records.add(record);
            }
            forestLossRepository.saveAll(records);

            AlertRule globalRule = new AlertRule();
            globalRule.setNombre("Regla Global SIMFAT");
            globalRule.setRegionId(null);
            globalRule.setUmbralPorcentajePerdida(0.5);
            globalRule.setUmbralEventosCalor(3);
            globalRule.setActiva(true);
            alertRuleRepository.save(globalRule);

            System.out.printf(
                Locale.ROOT,
                "[EXAM-IMPORT] Import completado. Regiones: %d, Perdidas: %d, Backup: %s%n",
                savedRegions.size(),
                records.size(),
                backupDir.toAbsolutePath()
            );
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.seed.exam.rollback.enabled", havingValue = "true")
    CommandLineRunner rollbackExamImport(
        RegionRepository regionRepository,
        ForestLossRecordRepository forestLossRepository,
        HeatAlertEventRepository heatAlertEventRepository,
        AlertRuleRepository alertRuleRepository,
        ObjectMapper objectMapper,
        @Value("${app.seed.exam.rollback.path}") String rollbackPathValue
    ) {
        return args -> {
            Path rollbackPath = Paths.get(rollbackPathValue);
            if (!Files.exists(rollbackPath)) {
                throw new IllegalStateException("No se encontro el directorio de rollback: " + rollbackPath.toAbsolutePath());
            }

            List<Region> regions = readList(objectMapper, rollbackPath.resolve("regions.json"), Region.class);
            List<ForestLossRecord> losses = readList(objectMapper, rollbackPath.resolve("forest_loss_records.json"), ForestLossRecord.class);
            List<HeatAlertEvent> alerts = readList(objectMapper, rollbackPath.resolve("heat_alert_events.json"), HeatAlertEvent.class);
            List<AlertRule> rules = readList(objectMapper, rollbackPath.resolve("alert_rules.json"), AlertRule.class);

            forestLossRepository.deleteAll();
            heatAlertEventRepository.deleteAll();
            alertRuleRepository.deleteAll();
            regionRepository.deleteAll();

            if (!regions.isEmpty()) {
                regionRepository.saveAll(regions);
            }
            if (!losses.isEmpty()) {
                forestLossRepository.saveAll(losses);
            }
            if (!alerts.isEmpty()) {
                heatAlertEventRepository.saveAll(alerts);
            }
            if (!rules.isEmpty()) {
                alertRuleRepository.saveAll(rules);
            }

            System.out.printf(
                Locale.ROOT,
                "[EXAM-ROLLBACK] Restaurado. Regiones: %d, Perdidas: %d, Alertas: %d, Reglas: %d, Backup: %s%n",
                regions.size(),
                losses.size(),
                alerts.size(),
                rules.size(),
                rollbackPath.toAbsolutePath()
            );
        };
    }

    private static Path createJsonBackup(
        RegionRepository regionRepository,
        ForestLossRecordRepository forestLossRepository,
        HeatAlertEventRepository heatAlertEventRepository,
        AlertRuleRepository alertRuleRepository,
        ObjectMapper objectMapper
    ) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupDir = Paths.get("backups", "exam-import-" + timestamp);
        Files.createDirectories(backupDir);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupDir.resolve("regions.json").toFile(), regionRepository.findAll());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            backupDir.resolve("forest_loss_records.json").toFile(),
            forestLossRepository.findAll()
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            backupDir.resolve("heat_alert_events.json").toFile(),
            heatAlertEventRepository.findAll()
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupDir.resolve("alert_rules.json").toFile(), alertRuleRepository.findAll());

        return backupDir;
    }

    private static <T> List<T> readList(ObjectMapper objectMapper, Path filePath, Class<T> elementType) throws IOException {
        if (!Files.exists(filePath)) {
            return List.of();
        }
        return objectMapper
            .readerForListOf(elementType)
            .readValue(filePath.toFile());
    }

    private static String inferZoneByRegionId(int regionId) {
        if (regionId == 15 || regionId <= 4) {
            return "NORTE";
        }
        if (regionId == 5 || regionId == 13 || regionId == 6 || regionId == 7) {
            return "CENTRO";
        }
        if (regionId == 16 || regionId == 8 || regionId == 9 || regionId == 14 || regionId == 10) {
            return "SUR";
        }
        return "AUSTRAL";
    }

    private static String normalizeMojibake(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value;
        if (normalized.contains("Ã") || normalized.contains("â")) {
            normalized = new String(normalized.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }

        return normalized
            .replace("’", "'")
            .replace("`", "'")
            .trim();
    }

    private record LossRow(int regionNumericId, int year, double hectaresLost) {}
}
