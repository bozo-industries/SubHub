# Pass 59: reject narrow animated-column motion

Status: reproduced false-motion case fixed in shadow observer; no live motion authority.

New failing test proved that a single textured animated column extending across the frame could
pass vertical-band consensus while the other columns were featureless. That is insufficient
evidence of whole-viewport movement and could create ghosts if later used for presentation.

Each reliable vertical band now also requires two textured columns supporting its selected
displacement, with their own error and stationary-improvement checks. Three vertical bands
are still required. This does not solve the inherently ambiguous case of a large animated
region dominating the image; recent-scroll gating and clean-source controls remain necessary.

Nine targeted estimator/observer JVM tests pass, including the initially failing regression.
Exact saved-descriptor probe still accepts the -48px/3band and -12px/4band transitions and
rejects weak intervals. No threshold was relaxed. No device touched or deployment performed.

Next: isolated CPU-vs-wall measurement and wider clean-source controls before any presentation
integration. Emulator timing environment remains uncalibrated; do not attribute its general
slowdown to this algorithm. Pixel released, shadow/GPU/anchors OFF. Goal active; weekly10%
remaining at start, pause5%.
