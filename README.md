# RouteProfile

A Hammerhead Karoo extension with one graphical field, **Route profile**: the whole route's
elevation coloured by gradient, where you are on it, and the distance and climb still to go.
Made for the bottom row of the map page (full width, about 478×126 px on a Karoo 3).

- Done part grey; ahead coloured by gradient: green < 3 %, yellow 3–6, orange 6–9, red 9–12,
  dark red ≥ 12, blue descent.
- Top right: kilometres and metres of climb to the end of the route.
- Tap the field: whole route / next 10 km.
- Auto zoom (in the app, on by default): the next 10 km when a climb starts within 2 km and
  while you are on it. One switch per climb category, scored as length (m) × average grade (%):
  Cat 4 from 8,000, Cat 3 from 16,000, Cat 2 from 32,000, Cat 1 from 64,000, HC from 80,000;
  below 8,000 uncategorized (off by default).
- No route: "No route".

Data: the route the Karoo navigates (its elevation polyline carries the distance along the
route), the Karoo's distance to destination, or the GPS fix projected on the route.

Status: first version, not released.

MIT licence.
