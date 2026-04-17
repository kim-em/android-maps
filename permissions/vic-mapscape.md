# VIC — FFM Mapscape tile permission request

**Status:** email drafted, not yet sent
**To:** vicmap@transport.vic.gov.au
**Date:** (pending)

---

**Subject:** Use of FFM Mapscape tiles in a free, open-source topo map app

Hi,

I'm developing AusTopo (https://github.com/kim-em/austopo), a free, open-source Android app for viewing Australian topographic maps. The app displays tiles from publicly accessible government ArcGIS REST endpoints for each state — NSW Spatial Services, QSpatial, Location SA, theLIST, and Geoscience Australia's national basemap for the NT and WA.

For Victoria, we're using the Mapscape topographic basemap served from emap.ffm.vic.gov.au/arcgis/rest/services/mapscape_mercator/MapServer. The tiles are excellent — significantly better than any other source available for Victoria. However, unlike the other states' endpoints (which publish CC BY metadata or are covered by explicit open data policies), this service has empty copyrightText in its ArcGIS metadata, and I haven't been able to find published terms of use for it.

I'm writing to ask:

1. Is it permissible to consume these tiles in a free, open-source app? AusTopo has no ads, no accounts, no commercial model — the source code is public and the app is distributed at no cost.
2. What licence applies, and what attribution text should we display?
3. Are there rate limits or usage constraints we should respect?

For context, the app caches tiles locally and respects each server's maxLod. It identifies itself with a descriptive User-Agent header (`AusTopo/<version>`). We're preparing for Google Play publication, and I want to make sure we're on solid ground before submitting.

I'm aware that the underlying Vicmap data is CC BY 4.0 per the DataVic Access Policy, and that the Victorian Government's default position is to make datasets freely available (DataVic Access Policy Guidelines, Principle 1: "Government data will be made available unless access is restricted for reasons of privacy, public safety, security and law enforcement, public health, and compliance with the law"). I also note that the 2015 VAGO audit on Access to Public Sector Information recommended that agencies do more to provide open access to government data, and that the government is currently updating its open data policy to reaffirm these commitments. If there's a way to bring this particular tile service under those policies, that would be ideal.

Happy to provide any further details about the app or its usage patterns.

Thanks,
Kim Morrison
kim@tqft.net
