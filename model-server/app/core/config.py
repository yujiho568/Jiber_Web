import os
from dataclasses import dataclass
from functools import lru_cache
from typing import Optional


DEFAULT_MODEL_VERSION = "hedonic-skeleton-v1"
DEFAULT_FEATURE_SET_VERSION = "apartment-basic-skeleton-v1"


@dataclass(frozen=True)
class Settings:
    internal_token: str
    model_version: str
    model_baseline_date: Optional[str]
    feature_set_version: str


@lru_cache
def get_settings() -> Settings:
    return Settings(
        internal_token=os.getenv("MODEL_SERVER_INTERNAL_TOKEN", "").strip(),
        model_version=os.getenv("MODEL_VERSION", "").strip() or DEFAULT_MODEL_VERSION,
        model_baseline_date=os.getenv("MODEL_BASELINE_DATE", "").strip() or None,
        feature_set_version=(
            os.getenv("MODEL_FEATURE_SET_VERSION", "").strip()
            or DEFAULT_FEATURE_SET_VERSION
        ),
    )
