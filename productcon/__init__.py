"""ProductCon – einheitliche Produktbilder auf Military-Hintergrund."""

__version__ = "1.0.0"

from .background import render_background  # noqa: E402
from .compose import compose  # noqa: E402
from .config import Settings  # noqa: E402
from .cutout import load_image, prepare_product  # noqa: E402

__all__ = ["Settings", "compose", "load_image", "prepare_product", "render_background", "__version__"]
