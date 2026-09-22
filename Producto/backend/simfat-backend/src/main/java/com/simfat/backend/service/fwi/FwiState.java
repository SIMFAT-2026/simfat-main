package com.simfat.backend.service.fwi;

/**
 * The three carried-forward Canadian Forest Fire Weather Index codes (Van Wagner &amp; Pickett
 * 1985): Fine Fuel Moisture Code, Duff Moisture Code and Drought Code. These persist day to day;
 * {@link CanadianFwiCalculator#advance(FwiState, FwiInputs)} consumes yesterday's state and
 * produces today's.
 */
public record FwiState(double ffmc, double dmc, double dc) {}
