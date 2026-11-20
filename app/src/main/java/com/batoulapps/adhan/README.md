# adhan-java 1.2.1, with two fixes

The source of [adhan-java](https://github.com/batoulapps/adhan-java) 1.2.1 (tag `v1.2.1`, `adhan/src/main/java`),
MIT, Copyright (c) 2016 Batoul Apps; its licence is `app/src/main/assets/licenses/adhan-java.txt`. Awan TV took it
in as source in 1.26.1a, in place of the Gradle dependency, to carry two fixes that adhan-java will not get:
adhan-js made them (pull request #243, merged 2026-11-15, after Awan TV's report, batoulapps/Adhan issue #117), and
the maintainer ports them to adhan-kotlin only.

1. `internal/Astronomical.correctedHourAngle`: adhan-js's bracketed iteration in place of one correction.
2. `internal/SolarTime.afternoon`: the noon shadow from the declination at the transit, not at 0h UT.

Nothing else differs from 1.2.1. Checked: 34,006 cities, every day of 2027 (Awan TV's own times, each country's
method and MWL Hanafi); against a precise ephemeris and the official tables in adhan's own test data.
