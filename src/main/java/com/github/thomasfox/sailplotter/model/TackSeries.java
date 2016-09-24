package com.github.thomasfox.sailplotter.model;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class TackSeries
{
  public double weighedSumMainPartBearingStarboard;

  public double weighedSumMainPartBearingPort;

  public double weighedSumMainPartVelocityStarboard;

  public double weighedSumMainPartVelocityPort;

  public double weighSumStarboard;

  public double weighSumPort;

  private Boolean starboardBearingCloseTo360Degrees;

  private Boolean portBearingCloseTo360Degrees;

  public Integer startTackIndex;

  public Integer endTackIndex;

  public final List<Tack> tacks = new ArrayList<>();

  public TackSeries(int startAndEndTackIndex)
  {
    this.startTackIndex = startAndEndTackIndex;
    this.endTackIndex = startAndEndTackIndex;
  }

  public void addTack(Tack tack, int tackIndex)
  {
    this.tacks.add(tack);
    this.endTackIndex = tackIndex;
    if (tack.hasMainPoints())
    {
      DataPoint startPoint = tack.getAfterStartManeuver();
      DataPoint endPoint = tack.getBeforeEndManeuver();
      Double distance = startPoint.distance(endPoint);
      Double bearing = startPoint.getBearingTo(endPoint);
      if (tack.pointOfSail == PointOfSail.CLOSE_HAULED_PORT)
      {
        if (portBearingCloseTo360Degrees == null)
        {
          portBearingCloseTo360Degrees = (bearing > 3 * Math.PI / 2);
        }

        if (portBearingCloseTo360Degrees && bearing < Math.PI)
        {
          weighedSumMainPartBearingPort += (bearing + 2 * Math.PI) * distance;
        }
        else if (!portBearingCloseTo360Degrees && bearing > Math.PI)
        {
          weighedSumMainPartBearingPort += (bearing - 2 * Math.PI) * distance;
        }
        else
        {
          weighedSumMainPartBearingPort += bearing * distance;
        }

        weighedSumMainPartVelocityPort += endPoint.getVelocityInKnotsBetween(startPoint) * distance;
        weighSumPort += distance;
      }
      else
      {
        if (starboardBearingCloseTo360Degrees == null)
        {
          starboardBearingCloseTo360Degrees = (bearing > 3 * Math.PI / 2);
        }

        if (starboardBearingCloseTo360Degrees && bearing < Math.PI)
        {
          weighedSumMainPartBearingStarboard += (bearing + 2 * Math.PI) * distance;
        }
        else if (!starboardBearingCloseTo360Degrees && bearing > Math.PI)
        {
          weighedSumMainPartBearingStarboard += (bearing - 2 * Math.PI) * distance;
        }
        else
        {
          weighedSumMainPartBearingStarboard += bearing * distance;
        }
        weighedSumMainPartVelocityStarboard += endPoint.getVelocityInKnotsBetween(startPoint) * distance;
        weighSumStarboard += distance;
      }
    }
  }

  public int getNumberOfTacks()
  {
    return endTackIndex - startTackIndex + 1;
  }

  public Double getAverageBearingStarboard()
  {
    if (weighSumStarboard == 0)
    {
      return null;
    }
    double result = weighedSumMainPartBearingStarboard / weighSumStarboard;
    if (result < 0)
    {
      result += 2 * Math.PI;
    }
    return result;
  }

  public Double getAverageBearingPort()
  {
    if (weighSumPort == 0)
    {
      return null;
    }
    double result =  weighedSumMainPartBearingPort / weighSumPort;
    if (result < 0)
    {
      result += 2 * Math.PI;
    }
    return result;
  }


  /**
   * Calculates the average wind direction in arcs
   * assuming that every tack in the tack series
   * was sailed with the same closeness to the wind.
   */
  public Double getAverageWindDirection()
  {
    Double averageBearingPort = getAverageBearingPort();
    Double averageBearingStarboard = getAverageBearingStarboard();
    if (averageBearingPort == null || averageBearingStarboard == null)
    {
      return null;
    }
    double result = (averageBearingPort + averageBearingStarboard) / 2;
    if ((averageBearingPort < Math.PI && averageBearingStarboard > 3 * Math.PI / 2)
        || averageBearingStarboard < Math.PI/2 && averageBearingPort > 3 * Math.PI / 2)
    {
      result += Math.PI;
    }
    if (result > 2 * Math.PI)
    {
      result -= 2 * Math.PI;
    }
    return result;
  }

  public Integer getAverageWindDirectionInDegrees()
  {
    Double averageWindDirection = getAverageWindDirection();
    if (averageWindDirection == null)
    {
      return null;
    }
    return (int) (averageWindDirection * 180 / Math.PI);
  }

  public Double getAverageAngleToWind()
  {
    Double averagWindDirection = getAverageWindDirection();
    if (averagWindDirection == null)
    {
      return null;
    }
    double result = averagWindDirection - getAverageBearingStarboard();
    if (result < 0)
    {
      result += 2 * Math.PI;
    }
    return result;
  }

  public Integer getAverageAngleToWindInDegrees()
  {
    Double averageAngleToWind = getAverageAngleToWind();
    if (averageAngleToWind == null)
    {
      return null;
    }
    return (int) (averageAngleToWind * 180 / Math.PI);
  }

  public Double getAverageMainPartVelocityStarboard()
  {
    if (weighSumStarboard == 0)
    {
      return null;
    }
    return weighedSumMainPartVelocityStarboard / weighSumStarboard;
  }

  public Double getAverageMainPartVelocityPort()
  {
    if (weighSumPort == 0)
    {
      return null;
    }
    return weighedSumMainPartVelocityPort / weighSumPort;
  }

  @Override
  public String toString()
  {
    StringBuilder result = new StringBuilder()
        .append("Tack series containing tacks " + startTackIndex + " to " + endTackIndex);
    if (getAverageWindDirection() != null)
    {
      result.append(", wind Direction: ")
          .append(new DecimalFormat("0").format(getAverageWindDirection() * 180 / Math.PI));
    }
    if (getAverageAngleToWind() != null)
    {
      result.append(", angle to wind: ")
          .append(new DecimalFormat("0").format(getAverageAngleToWind() * 180 / Math.PI));
    }
    if (getAverageMainPartVelocityStarboard() != null)
    {
      result.append(", mainPart velocity Starboard: ")
          .append(new DecimalFormat("0.0").format(getAverageMainPartVelocityStarboard()));
    }
    if (getAverageMainPartVelocityPort() != null)
    {
      result.append(", mainPart velocity Port: ")
          .append(new DecimalFormat("0.0").format(getAverageMainPartVelocityPort()));
    }
    return result.toString();
  }
}
