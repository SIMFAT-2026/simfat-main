package com.simfat.backend.service.fwi;

/**
 * Pure implementation of the Canadian Forest Fire Weather Index (FWI) System, following Van
 * Wagner, C.E. &amp; Pickett, T.L. (1985), "Equations and FORTRAN Program for the Canadian Forest
 * Fire Weather Index System," Forestry Technical Report 33, Canadian Forestry Service.
 *
 * <p>This class has NO Spring annotations, NO database access, NO HTTP calls and NO file I/O. It
 * is a deterministic function of (yesterday's codes, today's noon weather) -&gt; (today's codes and
 * indices). Persistence, scheduling and data sourcing are the responsibility of later slices
 * (S1d: {@code comuna_fwi_state} + Open-Meteo noon inputs; S1e: spin-up).
 *
 * <p>Reference values for this implementation are in {@code
 * src/test/resources/fwi/vanwagner-1985-vectors.csv}, transcribed from the original report's
 * "Sample of Output" table (Open Government Licence - Canada). See {@code
 * CanadianFwiCalculatorTest} for the full 49-day reference replay.
 */
public final class CanadianFwiCalculator {

    /**
     * The FFMC moisture-content constant used by Van Wagner &amp; Pickett (1985), Eq. 1 (moisture
     * from code) and Eq. 10 (code from moisture): {@code m = 147.2*(101-F)/(59.5+F)} and {@code
     * F = 59.5*(250-m)/(147.2+m)}.
     *
     * <p><b>This is the ORIGINAL Van Wagner constant, NOT the cffdrs R package's derived constant
     * 147.27723</b> (= 250*59.5/101, which cffdrs uses so the two equations are exact algebraic
     * inverses of each other). The original report uses 147.2 in both equations despite the small
     * resulting numerical inconsistency between them; this is a documented historical quirk of the
     * published FWI system, not a bug. Project decision (2026-09-20, Q15): use 147.2 exclusively
     * and never mix it with 147.27723 or with reference vectors computed under 147.27723.
     */
    private static final double FFMC_CONSTANT = 147.2;

    /**
     * Effective day-length adjustment factor for the DMC drying term, by calendar month
     * (index 0 = January), northern hemisphere table (Van Wagner &amp; Pickett 1985, Table/DATA
     * statement).
     */
    private static final double[] NORTHERN_HEMISPHERE_LE = {
        6.5, 7.5, 9.0, 12.8, 13.9, 13.9, 12.4, 10.9, 9.4, 8.0, 7.0, 6.0
    };

    /**
     * Day-length adjustment factor for the DC drying term, by calendar month (index 0 = January),
     * northern hemisphere table (Van Wagner &amp; Pickett 1985, Table/DATA statement).
     */
    private static final double[] NORTHERN_HEMISPHERE_LF = {
        -1.6, -1.6, -1.6, 0.9, 3.8, 5.8, 6.4, 5.0, 2.4, 0.4, -1.6, -1.6
    };

    private final double[] monthlyLe;
    private final double[] monthlyLf;

    /**
     * Creates a calculator using the given monthly day-length tables. Exposed so a later slice can
     * wire southern-hemisphere tables (e.g. for Chile, ~-37 latitude) without touching the
     * arithmetic in this class.
     *
     * @param monthlyLe 12 entries, January first, DMC effective day-length factor
     * @param monthlyLf 12 entries, January first, DC day-length adjustment factor
     */
    public CanadianFwiCalculator(double[] monthlyLe, double[] monthlyLf) {
        if (monthlyLe == null || monthlyLe.length != 12) {
            throw new IllegalArgumentException("monthlyLe must have exactly 12 entries (Jan..Dec)");
        }
        if (monthlyLf == null || monthlyLf.length != 12) {
            throw new IllegalArgumentException("monthlyLf must have exactly 12 entries (Jan..Dec)");
        }
        this.monthlyLe = monthlyLe.clone();
        this.monthlyLf = monthlyLf.clone();
    }

    /** Creates a calculator using the published northern-hemisphere day-length tables. */
    public CanadianFwiCalculator() {
        this(NORTHERN_HEMISPHERE_LE, NORTHERN_HEMISPHERE_LF);
    }

    /** Convenience factory, equivalent to {@code new CanadianFwiCalculator()}. */
    public static CanadianFwiCalculator northernHemisphere() {
        return new CanadianFwiCalculator();
    }

    /**
     * Advances the FWI state by one day.
     *
     * @param previousState yesterday's FFMC/DMC/DC (or the startup/spin-up state)
     * @param todayNoon today's noon weather observation
     * @return today's codes, indices and DSR
     */
    public FwiOutputs advance(FwiState previousState, FwiInputs todayNoon) {
        double ffmc = computeFfmc(previousState.ffmc(), todayNoon);
        double dmc = computeDmc(previousState.dmc(), todayNoon);
        double dc = computeDc(previousState.dc(), todayNoon);
        double isi = computeIsi(ffmc, todayNoon.windKmh());
        double bui = computeBui(dmc, dc);
        double fwi = computeFwi(isi, bui);
        double dsr = 0.0272 * Math.pow(fwi, 1.77);
        return new FwiOutputs(ffmc, dmc, dc, isi, bui, fwi, dsr);
    }

    /** Fine Fuel Moisture Code (Van Wagner &amp; Pickett 1985, Eq. 1-10). */
    private double computeFfmc(double previousFfmc, FwiInputs in) {
        double temp = in.tempC();
        double rh = in.rhPct();
        double wind = in.windKmh();
        double rain = in.precipMm();

        double mo = FFMC_CONSTANT * (101 - previousFfmc) / (59.5 + previousFfmc);

        if (rain > 0.5) {
            double rf = rain - 0.5;
            double mr = mo + 42.5 * rf * Math.exp(-100 / (251 - mo)) * (1 - Math.exp(-6.93 / rf));
            if (mo > 150) {
                mr += 0.0015 * Math.pow(mo - 150, 2) * Math.sqrt(rf);
            }
            if (mr > 250) {
                mr = 250;
            }
            mo = mr;
        }

        double ed =
                0.942 * Math.pow(rh, 0.679)
                        + 11 * Math.exp((rh - 100) / 10)
                        + 0.18 * (21.1 - temp) * (1 - Math.exp(-0.115 * rh));

        double m;
        if (mo > ed) {
            double ko =
                    0.424 * (1 - Math.pow(rh / 100, 1.7))
                            + 0.0694 * Math.sqrt(wind) * (1 - Math.pow(rh / 100, 8));
            double kd = ko * 0.581 * Math.exp(0.0365 * temp);
            m = ed + (mo - ed) * Math.pow(10, -kd);
        } else {
            double ew =
                    0.618 * Math.pow(rh, 0.753)
                            + 10 * Math.exp((rh - 100) / 10)
                            + 0.18 * (21.1 - temp) * (1 - Math.exp(-0.115 * rh));
            if (mo < ew) {
                double kl =
                        0.424 * (1 - Math.pow((100 - rh) / 100, 1.7))
                                + 0.0694 * Math.sqrt(wind) * (1 - Math.pow((100 - rh) / 100, 8));
                double kw = kl * 0.581 * Math.exp(0.0365 * temp);
                m = ew - (ew - mo) * Math.pow(10, -kw);
            } else {
                m = mo;
            }
        }

        double f = 59.5 * (250 - m) / (FFMC_CONSTANT + m);
        if (f > 101) {
            f = 101;
        }
        if (f < 0) {
            f = 0;
        }
        return f;
    }

    /** Duff Moisture Code (Van Wagner &amp; Pickett 1985, Eq. 11-16). */
    private double computeDmc(double previousDmc, FwiInputs in) {
        double temp = in.tempC();
        double rh = in.rhPct();
        double rain = in.precipMm();
        double le = monthlyLe[in.month() - 1];

        double p0 = previousDmc;

        if (rain > 1.5) {
            double re = 0.92 * rain - 1.27;
            // NOTE: the constant 0.023 here is NOT algebraically identical to 1/43.43
            // (0.023 vs 0.0230278...); the published equation uses the literal 0.023, so this
            // must not be "simplified" to exp(5.6348 - p0/43.43) -- that form was tried first
            // and produced a systematic, growing positive bias against the 1985 reference
            // vectors (traced by a diagnostic run of the full 49-day replay).
            double mo = 20 + 280 * Math.exp(-0.023 * p0);
            double b;
            if (p0 <= 33) {
                b = 100 / (0.5 + 0.3 * p0);
            } else if (p0 <= 65) {
                b = 14 - 1.3 * Math.log(p0);
            } else {
                b = 6.2 * Math.log(p0) - 17.2;
            }
            double mr = mo + 1000 * re / (48.77 + b * re);
            double pr = 244.72 - 43.43 * Math.log(mr - 20);
            if (pr < 0) {
                pr = 0;
            }
            p0 = pr;
        }

        double k;
        if (temp < -1.1) {
            k = 0;
        } else {
            k = 1.894 * (temp + 1.1) * (100 - rh) * le * 1.0e-6;
        }

        return p0 + 100 * k;
    }

    /** Drought Code (Van Wagner &amp; Pickett 1985, Eq. 17-21). */
    private double computeDc(double previousDc, FwiInputs in) {
        double temp = in.tempC();
        double rain = in.precipMm();
        double lf = monthlyLf[in.month() - 1];

        double d0 = previousDc;

        if (rain > 2.8) {
            double rd = 0.83 * rain - 1.27;
            double qo = 800 * Math.exp(-d0 / 400);
            double qr = qo + 3.937 * rd;
            double dr = 400 * Math.log(800 / qr);
            if (dr < 0) {
                dr = 0;
            }
            d0 = dr;
        }

        // Van Wagner & Pickett (1985), Eq. 20: T is floored at -2.8 degC BEFORE the multiplication,
        // then V itself is floored at 0. This is NOT equivalent to the DMC K-term's "temp < -1.1 ->
        // k = 0" shortcut: DMC's K is a pure product that happens to cancel to zero at its floor,
        // while here Lf is ADDITIVE after the temperature term, so it does not cancel -- omitting
        // the T floor silently under-counts DC growth whenever temp < -2.8 degC.
        double t = Math.max(temp, -2.8);
        double v = 0.36 * (t + 2.8) + lf;
        if (v < 0) {
            v = 0;
        }

        return d0 + 0.5 * v;
    }

    /** Initial Spread Index (Van Wagner &amp; Pickett 1985, Eq. 24-26). */
    private double computeIsi(double ffmc, double windKmh) {
        double m = FFMC_CONSTANT * (101 - ffmc) / (59.5 + ffmc);
        double fW = Math.exp(0.05039 * windKmh);
        double fF = 91.9 * Math.exp(-0.1386 * m) * (1 + Math.pow(m, 5.31) / 4.93e7);
        return 0.208 * fW * fF;
    }

    /** Buildup Index (Van Wagner &amp; Pickett 1985, Eq. 27-28). */
    private double computeBui(double dmc, double dc) {
        if (dmc + 0.4 * dc <= 0) {
            return 0;
        }
        double bui;
        if (dmc <= 0.4 * dc) {
            bui = 0.8 * dmc * dc / (dmc + 0.4 * dc);
        } else {
            bui =
                    dmc
                            - (1 - 0.8 * dc / (dmc + 0.4 * dc))
                                    * (0.92 + Math.pow(0.0114 * dmc, 1.7));
        }
        return bui < 0 ? 0 : bui;
    }

    /** Fire Weather Index and its Daily Severity Rating (Van Wagner &amp; Pickett 1985, Eq. 28-30). */
    private double computeFwi(double isi, double bui) {
        double fD;
        if (bui <= 80) {
            fD = 0.626 * Math.pow(bui, 0.809) + 2;
        } else {
            fD = 1000 / (25 + 108.64 * Math.exp(-0.023 * bui));
        }
        double b = 0.1 * isi * fD;
        if (b > 1) {
            return Math.exp(2.72 * Math.pow(0.434 * Math.log(b), 0.647));
        }
        return b;
    }
}
