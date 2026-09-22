package com.simfat.backend.service.fwi;

/**
 * Today's noon-local-standard-time weather observation, as required by the Canadian Forest Fire
 * Weather Index System (Van Wagner &amp; Pickett 1985).
 *
 * @param tempC noon dry-bulb temperature, degrees Celsius
 * @param rhPct noon relative humidity, percent (0-100)
 * @param windKmh noon wind speed, km/h
 * @param precipMm 24-hour accumulated precipitation ending at noon, mm
 * @param month calendar month (1-12), used to select the DMC/DC day-length adjustment factors
 */
public record FwiInputs(double tempC, double rhPct, double windKmh, double precipMm, int month) {}
