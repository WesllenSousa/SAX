package net.seninp.jmotif.sax.discord;

import java.util.Date;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.seninp.jmotif.distance.EuclideanDistance;
import net.seninp.jmotif.sax.SAXProcessor;
import net.seninp.jmotif.sax.TSProcessor;
import net.seninp.jmotif.sax.registry.LargeWindowAlgorithm;
import net.seninp.jmotif.sax.registry.VisitRegistry;

/**
 * Implements SAX-based discord finder, i.e. HOT-SAX.
 * 
 * @author psenin
 * 
 */
public class BruteForceDiscordImplementation {

  // logging stuff
  //
  private static Logger consoleLogger;
  private static Level LOGGING_LEVEL = Level.INFO;

  static {
    consoleLogger = (Logger) LoggerFactory.getLogger(BruteForceDiscordImplementation.class);
    consoleLogger.setLevel(LOGGING_LEVEL);
  }

  private static TSProcessor tsProcessor = new TSProcessor();
  private static EuclideanDistance ed = new EuclideanDistance();

  /**
   * Constructor.
   */
  public BruteForceDiscordImplementation() {
    super();
  }

  /**
   * Brute force discord search implementation. BRUTE FORCE algorithm.
   * 
   * @param series the data we work with.
   * @param windowSize the sliding window size.
   * @param discordCollectionSize the number of discords we look for.
   * @param marker the marker window algorithm implementation.
   * @return discords.
   * @throws Exception if error occurs.
   */
  public static DiscordRecords series2BruteForceDiscords(double[] series, Integer windowSize,
      int discordCollectionSize, LargeWindowAlgorithm marker) throws Exception {

    DiscordRecords discords = new DiscordRecords();

    // init new registry to the full length, but mark the end of it
    //
    VisitRegistry globalTrackVisitRegistry = new VisitRegistry(series.length);
    globalTrackVisitRegistry.markVisited(series.length - windowSize, series.length);

    int discordCounter = 0;

    while (discords.getSize() < discordCollectionSize) {

      consoleLogger.debug(
          "currently known discords: " + discords.getSize() + " out of " + discordCollectionSize);

      // mark start and number of iterations
      Date start = new Date();

      DiscordRecord bestDiscord = findBestDiscordBruteForce(series, windowSize,
          globalTrackVisitRegistry, marker);
      bestDiscord.setPayload("#" + discordCounter);
      Date end = new Date();

      // if the discord is null we getting out of the search
      if (bestDiscord.getNNDistance() == 0.0D || bestDiscord.getPosition() == -1) {
        consoleLogger.debug("breaking the outer search loop, discords found: " + discords.getSize()
            + " last seen discord: " + bestDiscord.toString());
        break;
      }

      bestDiscord.setInfo(
          "position " + bestDiscord.getPosition() + ", NN distance " + bestDiscord.getNNDistance()
              + ", elapsed time: " + SAXProcessor.timeToString(start.getTime(), end.getTime())
              + ", " + bestDiscord.getInfo());
      consoleLogger.debug(bestDiscord.getInfo());

      // collect the result
      //
      discords.add(bestDiscord);

      // and maintain data structures
      //
      marker.markVisited(globalTrackVisitRegistry, bestDiscord.getPosition(), windowSize);

      discordCounter++;
    }

    // done deal
    //
    return discords;
  }

  /**
   * Finds the best discord. BRUTE FORCE algorithm.
   * 
   * @param series the data.
   * @param windowSize the SAX sliding window size.
   * @param globalRegistry the visit registry to use.
   * @param marker the marker algorithm implementation.
   * @return the best discord with respect to registry.
   * @throws Exception if error occurs.
   */
  public static DiscordRecord findBestDiscordBruteForce(double[] series, Integer windowSize,
      VisitRegistry globalRegistry, LargeWindowAlgorithm marker) throws Exception {

    Date start = new Date();

    long distanceCallsCounter = 0;

    double bestSoFarDistance = -1;
    int bestSoFarPosition = -1;

    VisitRegistry localRegistry = globalRegistry.clone();

    int i = -1;
    while (-1 != (i = localRegistry.getNextRandomUnvisitedPosition())) { // outer loop

      // shall not happen!
      //
      // if (i >= series.length - windowSize - 1) {
      // continue;
      // }

      // check the global visits registry
      if (globalRegistry.isVisited(i, i + windowSize)) {
        continue;
      }

      double[] cw = tsProcessor.subseriesByCopy(series, i, i + windowSize);
      double nearestNeighborDistance = Double.MAX_VALUE;

      for (int j = 1; j < series.length - windowSize; j++) { // inner loop

        if (Math.abs(i - j) > windowSize) { // > means they shall not overlap even in a single point

          double[] currentSubsequence = tsProcessor.subseriesByCopy(series, j, j + windowSize);

          double dist = ed.earlyAbandonedDistance(cw, currentSubsequence, nearestNeighborDistance);

          distanceCallsCounter++;

          if ((!Double.isNaN(dist)) && dist < nearestNeighborDistance) {
            nearestNeighborDistance = dist;
          }
        }

      }

      if (!(Double.isInfinite(nearestNeighborDistance))
          && nearestNeighborDistance > bestSoFarDistance) {
        bestSoFarDistance = nearestNeighborDistance;
        bestSoFarPosition = i;
        consoleLogger
            .debug("discord updated: pos " + bestSoFarPosition + ", dist " + bestSoFarDistance);
      }

      localRegistry.markVisited(i);

    }
    Date firstDiscord = new Date();

    consoleLogger.debug(
        "best discord found at " + bestSoFarPosition + ", best distance: " + bestSoFarDistance
            + ", in " + SAXProcessor.timeToString(start.getTime(), firstDiscord.getTime())
            + " distance calls: " + distanceCallsCounter);

    DiscordRecord res = new DiscordRecord(bestSoFarPosition, bestSoFarDistance);
    res.setInfo("distance calls: " + distanceCallsCounter);
    return res;
  }

}
