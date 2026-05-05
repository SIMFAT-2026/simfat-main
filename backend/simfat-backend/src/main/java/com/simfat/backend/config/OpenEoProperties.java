package com.simfat.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openeo")
public class OpenEoProperties {

    private Service service = new Service();
    private Sync sync = new Sync();
    private Aoi aoi = new Aoi();
    private Ingest ingest = new Ingest();

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public Aoi getAoi() {
        return aoi;
    }

    public void setAoi(Aoi aoi) {
        this.aoi = aoi;
    }

    public Ingest getIngest() {
        return ingest;
    }

    public void setIngest(Ingest ingest) {
        this.ingest = ingest;
    }

    public static class Service {

        private String baseUrl = "http://localhost:8000";
        private int timeoutMs = 8000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Sync {

        private boolean enabled = true;
        private String cron = "0 */15 * * * *";
        private boolean placeholderValueEnabled = false;
        private int minRequestIntervalMinutes = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public boolean isPlaceholderValueEnabled() {
            return placeholderValueEnabled;
        }

        public void setPlaceholderValueEnabled(boolean placeholderValueEnabled) {
            this.placeholderValueEnabled = placeholderValueEnabled;
        }

        public int getMinRequestIntervalMinutes() {
            return minRequestIntervalMinutes;
        }

        public void setMinRequestIntervalMinutes(int minRequestIntervalMinutes) {
            this.minRequestIntervalMinutes = minRequestIntervalMinutes;
        }
    }

    public static class Aoi {

        // Format: CL-15:-70.8,-19.2,-69.2,-18.1;CL-1:-70.5,-21.0,-69.8,-20.2
        private String bboxMap = "";

        public String getBboxMap() {
            return bboxMap;
        }

        public void setBboxMap(String bboxMap) {
            this.bboxMap = bboxMap;
        }
    }

    public static class Ingest {

        private String authToken = "";

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }
    }
}
