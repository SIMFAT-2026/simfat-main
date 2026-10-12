"""Pure-Python parity port of CanadianFwiCalculator.java.

Ported line-for-line from
``Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/
fwi/CanadianFwiCalculator.java`` (Van Wagner, C.E. & Pickett, T.L. (1985),
"Equations and FORTRAN Program for the Canadian Forest Fire Weather Index
System," Forestry Technical Report 33, Canadian Forestry Service), so the
study's FWI chain matches production exactly. See that file's javadoc for
the full citation and reference-value provenance.

Only the northern-hemisphere day-length tables are implemented here, because
production (``ComunaFwiStateService``) still wires
``CanadianFwiCalculator.northernHemisphere()`` as of this study (southern-
hemisphere tables for Chile are a documented TODO in design D4, not yet
shipped) -- this is a PARITY port, so it must reproduce what production
actually runs, not a corrected version of it.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

# The ORIGINAL Van Wagner & Pickett (1985) constant (NOT the cffdrs R
# package's derived constant 147.27723). Project decision Q15 (2026-09-20):
# use 147.2 exclusively. See CanadianFwiCalculator.java's FFMC_CONSTANT.
FFMC_CONSTANT = 147.2

# Northern-hemisphere day-length tables (index 0 = January), Van Wagner &
# Pickett 1985 Table/DATA statement.
NORTHERN_HEMISPHERE_LE = (6.5, 7.5, 9.0, 12.8, 13.9, 13.9, 12.4, 10.9, 9.4, 8.0, 7.0, 6.0)
NORTHERN_HEMISPHERE_LF = (-1.6, -1.6, -1.6, 0.9, 3.8, 5.8, 6.4, 5.0, 2.4, 0.4, -1.6, -1.6)


@dataclass(frozen=True)
class FwiState:
    """The three carried-forward codes: Fine Fuel Moisture, Duff Moisture, Drought."""

    ffmc: float
    dmc: float
    dc: float


@dataclass(frozen=True)
class FwiInputs:
    """Today's noon-local-standard-time weather observation.

    Validation policy mirrors FwiInputs.java exactly: ``rh_pct`` is clamped
    to [0, 100] (RH cannot physically exceed 100%; a small sensor overshoot
    from Open-Meteo-like APIs is a measurement artifact, not an error worth
    crashing on -- and an unclamped rh > 100 makes the FFMC wetting branch
    compute a fractional power of a negative base, which is NaN). ``wind_kmh``
    and ``precip_mm`` are clamped to >= 0. ``month`` is validated strictly
    (raises) because it indexes a day-length table and an out-of-range value
    is a caller bug, not measurement noise.
    """

    temp_c: float
    rh_pct: float
    wind_kmh: float
    precip_mm: float
    month: int

    def __post_init__(self) -> None:
        if not (1 <= self.month <= 12):
            raise ValueError(f"month must be between 1 and 12, got {self.month}")
        object.__setattr__(self, "rh_pct", min(100.0, max(0.0, self.rh_pct)))
        object.__setattr__(self, "wind_kmh", max(0.0, self.wind_kmh))
        object.__setattr__(self, "precip_mm", max(0.0, self.precip_mm))


@dataclass(frozen=True)
class FwiOutputs:
    """Today's codes, derived indices and Daily Severity Rating."""

    ffmc: float
    dmc: float
    dc: float
    isi: float
    bui: float
    fwi: float
    dsr: float


def _compute_ffmc(previous_ffmc: float, inputs: FwiInputs) -> float:
    """Fine Fuel Moisture Code (Eq. 1-10)."""
    temp = inputs.temp_c
    rh = inputs.rh_pct
    wind = inputs.wind_kmh
    rain = inputs.precip_mm

    mo = FFMC_CONSTANT * (101 - previous_ffmc) / (59.5 + previous_ffmc)

    if rain > 0.5:
        rf = rain - 0.5
        mr = mo + 42.5 * rf * math.exp(-100 / (251 - mo)) * (1 - math.exp(-6.93 / rf))
        if mo > 150:
            mr += 0.0015 * (mo - 150) ** 2 * math.sqrt(rf)
        if mr > 250:
            mr = 250.0
        mo = mr

    ed = (
        0.942 * rh ** 0.679
        + 11 * math.exp((rh - 100) / 10)
        + 0.18 * (21.1 - temp) * (1 - math.exp(-0.115 * rh))
    )

    if mo > ed:
        ko = 0.424 * (1 - (rh / 100) ** 1.7) + 0.0694 * math.sqrt(wind) * (1 - (rh / 100) ** 8)
        kd = ko * 0.581 * math.exp(0.0365 * temp)
        m = ed + (mo - ed) * 10 ** (-kd)
    else:
        ew = (
            0.618 * rh ** 0.753
            + 10 * math.exp((rh - 100) / 10)
            + 0.18 * (21.1 - temp) * (1 - math.exp(-0.115 * rh))
        )
        if mo < ew:
            kl = 0.424 * (1 - ((100 - rh) / 100) ** 1.7) + 0.0694 * math.sqrt(wind) * (
                1 - ((100 - rh) / 100) ** 8
            )
            kw = kl * 0.581 * math.exp(0.0365 * temp)
            m = ew - (ew - mo) * 10 ** (-kw)
        else:
            m = mo

    f = 59.5 * (250 - m) / (FFMC_CONSTANT + m)
    if f > 101:
        f = 101.0
    if f < 0:
        f = 0.0
    return f


def _compute_dmc(previous_dmc: float, inputs: FwiInputs, monthly_le) -> float:
    """Duff Moisture Code (Eq. 11-16)."""
    temp = inputs.temp_c
    rh = inputs.rh_pct
    rain = inputs.precip_mm
    le = monthly_le[inputs.month - 1]

    p0 = previous_dmc

    if rain > 1.5:
        re = 0.92 * rain - 1.27
        # NOTE: the constant 0.023 here is the published literal, NOT
        # algebraically identical to 1/43.43 (0.023 vs 0.0230278...) -- see
        # CanadianFwiCalculator.java's computeDmc for the diagnostic that
        # found simplifying this introduces a systematic growing bias.
        mo = 20 + 280 * math.exp(-0.023 * p0)
        if p0 <= 33:
            b = 100 / (0.5 + 0.3 * p0)
        elif p0 <= 65:
            b = 14 - 1.3 * math.log(p0)
        else:
            b = 6.2 * math.log(p0) - 17.2
        mr = mo + 1000 * re / (48.77 + b * re)
        pr = 244.72 - 43.43 * math.log(mr - 20)
        if pr < 0:
            pr = 0.0
        p0 = pr

    if temp < -1.1:
        k = 0.0
    else:
        k = 1.894 * (temp + 1.1) * (100 - rh) * le * 1.0e-6

    return p0 + 100 * k


def _compute_dc(previous_dc: float, inputs: FwiInputs, monthly_lf) -> float:
    """Drought Code (Eq. 17-21)."""
    temp = inputs.temp_c
    rain = inputs.precip_mm
    lf = monthly_lf[inputs.month - 1]

    d0 = previous_dc

    if rain > 2.8:
        rd = 0.83 * rain - 1.27
        qo = 800 * math.exp(-d0 / 400)
        qr = qo + 3.937 * rd
        dr = 400 * math.log(800 / qr)
        if dr < 0:
            dr = 0.0
        d0 = dr

    # Eq. 20: T is floored at -2.8 degC BEFORE the multiplication, then V
    # itself is floored at 0. Omitting the T floor silently under-counts DC
    # growth whenever temp < -2.8 degC (see the Java dc_subFreezingTemp test).
    t = max(temp, -2.8)
    v = 0.36 * (t + 2.8) + lf
    if v < 0:
        v = 0.0

    return d0 + 0.5 * v


def _compute_isi(ffmc: float, wind_kmh: float) -> float:
    """Initial Spread Index (Eq. 24-26)."""
    m = FFMC_CONSTANT * (101 - ffmc) / (59.5 + ffmc)
    f_w = math.exp(0.05039 * wind_kmh)
    f_f = 91.9 * math.exp(-0.1386 * m) * (1 + m ** 5.31 / 4.93e7)
    return 0.208 * f_w * f_f


def _compute_bui(dmc: float, dc: float) -> float:
    """Buildup Index (Eq. 27-28)."""
    if dmc + 0.4 * dc <= 0:
        return 0.0
    if dmc <= 0.4 * dc:
        bui = 0.8 * dmc * dc / (dmc + 0.4 * dc)
    else:
        bui = dmc - (1 - 0.8 * dc / (dmc + 0.4 * dc)) * (0.92 + (0.0114 * dmc) ** 1.7)
    return bui if bui >= 0 else 0.0


def _compute_fwi(isi: float, bui: float) -> float:
    """Fire Weather Index (Eq. 28-30)."""
    if bui <= 80:
        f_d = 0.626 * bui ** 0.809 + 2
    else:
        f_d = 1000 / (25 + 108.64 * math.exp(-0.023 * bui))
    b = 0.1 * isi * f_d
    if b > 1:
        return math.exp(2.72 * (0.434 * math.log(b)) ** 0.647)
    return b


def advance(
    previous_state: FwiState,
    today_noon: FwiInputs,
    *,
    monthly_le=NORTHERN_HEMISPHERE_LE,
    monthly_lf=NORTHERN_HEMISPHERE_LF,
) -> FwiOutputs:
    """Advances the FWI state by one day. Mirrors CanadianFwiCalculator.advance."""
    ffmc = _compute_ffmc(previous_state.ffmc, today_noon)
    dmc = _compute_dmc(previous_state.dmc, today_noon, monthly_le)
    dc = _compute_dc(previous_state.dc, today_noon, monthly_lf)
    isi = _compute_isi(ffmc, today_noon.wind_kmh)
    bui = _compute_bui(dmc, dc)
    fwi_value = _compute_fwi(isi, bui)
    dsr = 0.0272 * fwi_value ** 1.77
    return FwiOutputs(ffmc, dmc, dc, isi, bui, fwi_value, dsr)
