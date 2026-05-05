from enum import Enum


class IndicatorType(str, Enum):
    NDVI = "NDVI"
    NDMI = "NDMI"


class JobStatus(str, Enum):
    ACCEPTED = "accepted"
    FAILED = "failed"
