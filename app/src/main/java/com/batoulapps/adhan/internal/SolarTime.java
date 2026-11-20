package com.batoulapps.adhan.internal;

import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.data.DateComponents;

public class SolarTime {

  public final double transit;
  public final double sunrise;
  public final double sunset;

  private final Coordinates observer;
  private final SolarCoordinates solar;
  private final SolarCoordinates prevSolar;
  private final SolarCoordinates nextSolar;
  private double approximateTransit;

  public SolarTime(DateComponents today, Coordinates coordinates) {
    final double julianDate = CalendricalHelper.julianDay(today.year, today.month, today.day);

    this.prevSolar = new SolarCoordinates(julianDate - 1);
    this.solar = new SolarCoordinates(julianDate);
    this.nextSolar = new SolarCoordinates(julianDate + 1);

    this.approximateTransit = Astronomical.approximateTransit(coordinates.longitude,
        solar.apparentSiderealTime, solar.rightAscension);
    final double solarAltitude = -50.0 / 60.0;

    this.observer = coordinates;
    this.transit = Astronomical.correctedTransit(this.approximateTransit, coordinates.longitude,
        solar.apparentSiderealTime, solar.rightAscension, prevSolar.rightAscension,
        nextSolar.rightAscension);
    this.sunrise = Astronomical.correctedHourAngle(this.approximateTransit, solarAltitude,
        coordinates, false, solar.apparentSiderealTime, solar.rightAscension,
        prevSolar.rightAscension, nextSolar.rightAscension, solar.declination,
        prevSolar.declination, nextSolar.declination);
    this.sunset = Astronomical.correctedHourAngle(this.approximateTransit, solarAltitude,
        coordinates, true, solar.apparentSiderealTime, solar.rightAscension,
        prevSolar.rightAscension, nextSolar.rightAscension, solar.declination,
        prevSolar.declination, nextSolar.declination);
  }

  public double hourAngle(double angle, boolean afterTransit) {
    return Astronomical.correctedHourAngle(this.approximateTransit, angle, this.observer,
        afterTransit, this.solar.apparentSiderealTime, this.solar.rightAscension,
        this.prevSolar.rightAscension, this.nextSolar.rightAscension, this.solar.declination,
        this.prevSolar.declination, this.nextSolar.declination);
  }

  // hours from transit
  public double afternoon(ShadowLength shadowLength) {
    // TODO (from Swift version) source shadow angle calculation
    // Awan TV (1.26.1a), as adhan-js (pull request #243, MIT): the noon shadow is cast at the transit, so the
    // declination there, not at 0h UT (up to a day away by the longitude): Asr was up to about a minute off.
    final double noonDeclination = Astronomical.interpolate(solar.declination, prevSolar.declination,
        nextSolar.declination, transit / 24);
    final double tangent = Math.abs(observer.latitude - noonDeclination);
    final double inverse = shadowLength.getShadowLength() + Math.tan(Math.toRadians(tangent));
    final double angle = Math.toDegrees(Math.atan(1.0 / inverse));

    return hourAngle(angle, true);
  }

}
