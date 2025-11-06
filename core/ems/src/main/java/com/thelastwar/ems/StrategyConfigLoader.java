package com.thelastwar.ems;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * StrategyConfigLoader loads strategy configurations from YAML files.
 * 
 * <p>Example YAML:</p>
 * <pre>
 * strategyId: "twap-001"
 * strategyType: "TWAP"
 * symbol: "AAPL"
 * duration: "PT10M"  # 10 minutes
 * numSlices: 10
 * minChildSize: 100
 * maxChildSize: 1000
 * priceLimit: 15000
 * allowPartialFills: true
 * parameters:
 *   urgency: "LOW"
 * </pre>
 */
public class StrategyConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(StrategyConfigLoader.class);
    
    private final ObjectMapper objectMapper;
    
    public StrategyConfigLoader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Load a single strategy configuration from a file.
     * 
     * @param path Path to YAML file
     * @return Loaded strategy configuration
     * @throws IOException if file cannot be read or parsed
     */
    public StrategyConfig loadFromFile(Path path) throws IOException {
        logger.info("Loading strategy config from {}", path);
        
        try (InputStream input = Files.newInputStream(path)) {
            return loadFromStream(input);
        }
    }
    
    /**
     * Load a single strategy configuration from an input stream.
     * 
     * @param input Input stream containing YAML
     * @return Loaded strategy configuration
     * @throws IOException if stream cannot be read or parsed
     */
    public StrategyConfig loadFromStream(InputStream input) throws IOException {
        Map<String, Object> data = objectMapper.readValue(input, Map.class);
        return buildConfigFromMap(data);
    }
    
    /**
     * Load multiple strategy configurations from a directory.
     * 
     * @param directory Directory containing YAML files
     * @return List of loaded configurations
     * @throws IOException if directory cannot be read
     */
    public List<StrategyConfig> loadFromDirectory(Path directory) throws IOException {
        logger.info("Loading strategy configs from directory {}", directory);
        
        return Files.list(directory)
            .filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
            .map(path -> {
                try {
                    return loadFromFile(path);
                } catch (IOException e) {
                    logger.error("Failed to load config from {}", path, e);
                    return null;
                }
            })
            .filter(config -> config != null)
            .toList();
    }
    
    /**
     * Build a StrategyConfig from a map.
     */
    private StrategyConfig buildConfigFromMap(Map<String, Object> data) {
        StrategyConfig.Builder builder = new StrategyConfig.Builder();
        
        if (data.containsKey("strategyId")) {
            builder.strategyId((String) data.get("strategyId"));
        }
        
        if (data.containsKey("strategyType")) {
            String typeStr = (String) data.get("strategyType");
            builder.strategyType(StrategyType.valueOf(typeStr.toUpperCase()));
        }
        
        if (data.containsKey("symbol")) {
            builder.symbol((String) data.get("symbol"));
        }
        
        if (data.containsKey("duration")) {
            String durationStr = (String) data.get("duration");
            builder.duration(Duration.parse(durationStr));
        }
        
        if (data.containsKey("numSlices")) {
            builder.numSlices(((Number) data.get("numSlices")).intValue());
        }
        
        if (data.containsKey("minChildSize")) {
            builder.minChildSize(((Number) data.get("minChildSize")).longValue());
        }
        
        if (data.containsKey("maxChildSize")) {
            builder.maxChildSize(((Number) data.get("maxChildSize")).longValue());
        }
        
        if (data.containsKey("priceLimit")) {
            builder.priceLimit(((Number) data.get("priceLimit")).longValue());
        }
        
        if (data.containsKey("allowPartialFills")) {
            builder.allowPartialFills((Boolean) data.get("allowPartialFills"));
        }
        
        if (data.containsKey("parameters")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) data.get("parameters");
            builder.parameters(params);
        }
        
        return builder.build();
    }
    
    /**
     * Save a strategy configuration to a file.
     * 
     * @param config Configuration to save
     * @param path Output file path
     * @throws IOException if file cannot be written
     */
    public void saveToFile(StrategyConfig config, Path path) throws IOException {
        logger.info("Saving strategy config to {}", path);
        objectMapper.writeValue(path.toFile(), config);
    }
}
