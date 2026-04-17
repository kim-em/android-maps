# SA — Location SA tile permission request

**Status:** email drafted, not yet sent
**To:** locationsa@sa.gov.au
**Date:** (pending)

---

**Subject:** Confirming CC BY 4.0 covers Location SA tile service for use in a free app

Hi,

I'm developing AusTopo (https://github.com/kim-em/austopo), a free, open-source Android app for viewing Australian topographic maps. For South Australia, the app displays tiles from location.sa.gov.au/arcgis/rest/services/BaseMaps/Topographic_wmas/MapServer.

The Location SA Map Viewer declares CC BY 4.0 in its footer, all Location SA datasets on data.sa.gov.au are listed under CC BY, and the SA Government's Open Data Principles specify CC BY 4.0 as the preferred licence (Principle 3) — so I suspect the answer here is straightforward. But the ArcGIS REST endpoint itself has empty metadata fields, and I'd like explicit confirmation before publishing the app on Google Play.

Specifically:

1. Does CC BY 4.0 cover the rendered tiles served via the Topographic_wmas MapServer REST endpoint, for consumption by a third-party mobile app?
2. Is the correct attribution "© Government of South Australia", or do you prefer different wording?

AusTopo is free, open-source, has no ads or commercial model, and identifies itself with a descriptive User-Agent header. We display attribution for all tile providers on the map view.

Thanks,
Kim Morrison
kim@tqft.net
