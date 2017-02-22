// Changelog:
// v1: refactor from python
// v2: targeting neutral square to do most damage possible

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.PrintWriter;


@SuppressWarnings("unchecked")
public class OldBot2 {
  // Global variables
  static GameMap gameMap;
  static InitPackage iPackage;
  static final int NEUTRAL = 0;
  static int myID;

  static Map<Integer, Integer> ownerProduction = new HashMap();
  static Map<Integer, Integer> ownerStrength = new HashMap();
  static Map<Integer, Integer> ownerTerritory = new HashMap();
  static Map<Location, Direction> outerRing = new HashMap();
  static Map<Location, InnerAreaMemory> innerArea = new HashMap();
  static PrintWriter log;

	public static void main(String[] args) throws Exception {
    boolean isFirstCycle = true;
		iPackage = Networking.getInit();
		myID = iPackage.myID;
		gameMap = iPackage.map;
    log = new PrintWriter("./log.txt", "UTF-8");

		Networking.sendInit("OldBot2");

		while(true) {
      // Cycle variables
			ArrayList<Move> moves = new ArrayList<Move>();
      ownerProduction.clear();
      ownerStrength.clear();
      ownerTerritory.clear();
      outerRing.clear();
      innerArea.clear();
			gameMap = Networking.getFrame();

      if (isFirstCycle) {
        initiate(); isFirstCycle = false;
      }

      initialSweep(gameMap);

      generateInnerAreaDiffusionMap();

      outerRingStrategy();

      for (Location l : outerRing.keySet()) {
        moves.add(new Move(l, outerRing.get(l)));
      }
      for (Location l : innerArea.keySet()) {
        moves.add(new Move(l, innerArea.get(l).dir));
      }
			Networking.sendFrame(moves);
		}
	}

  private static void initiate() {
    Location initialLoc;
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        // Compute best initial path with DFS
        Location loc = new Location(x, y);
        Site site = gameMap.getSite(loc);
        if (site.owner == myID) {
          initialLoc = loc;
        }
      }
    }
  }

  private static void initialSweep(GameMap gameMap) {
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location loc = new Location(x, y);
        Site site = gameMap.getSite(loc);

        // Compute player stats
        if (site.owner != NEUTRAL) {
          if (ownerProduction.containsKey(site.owner)) {
            ownerProduction.put(site.owner, ownerProduction.get(site.owner) + site.production);
            ownerStrength.put(site.owner, ownerStrength.get(site.owner) + site.strength);
            ownerTerritory.put(site.owner, ownerTerritory.get(site.owner) + 1);
          } else {
            ownerProduction.put(site.owner, site.production);
            ownerStrength.put(site.owner, site.strength);
            ownerTerritory.put(site.owner, 1);
          }
        }

        // Find outer ring and inner area
        if (site.owner == myID) {
          for (Direction d : Direction.CARDINALS) {
            if (gameMap.getSite(loc, d).owner != myID) {
              outerRing.put(loc, Direction.STILL);
              break;
            }
          }
          if (!outerRing.containsKey(loc)) {
            InnerAreaMemory m = new InnerAreaMemory(Direction.STILL, Integer.MAX_VALUE);
            innerArea.put(loc, m);
          }
        }
      }
    }
  }

  private static void outerRingStrategy() {
    for (Location l : outerRing.keySet()) {
      double highestProdRatio = 0.0;
      Direction prodRatioDirection = Direction.STILL;
      Direction enemyDirection = Direction.STILL;
      int highestPossibleEnemyDamage = 0;
      int lowestStr = 256;
      for (Direction d : Direction.CARDINALS) {
        Site current = gameMap.getSite(l);
        Location targetLoc = gameMap.getLocation(l, d);
        Site target = gameMap.getSite(targetLoc);

        // Target highest accessible prod ratio
        if (target.owner != myID && current.strength > target.strength) {
          double targetProdRatio = (double)target.production / (target.strength+1) + 0.0001; // Take squares with zero production
          if (targetProdRatio > highestProdRatio) {
            prodRatioDirection = d;
            highestProdRatio = (double)target.production / (target.strength+1);
          }
        }

        // Target enemy
        int possibleEnemyDamage = 0;
        if (target.owner != myID && target.strength == 0) {
          for (Direction d2 : Direction.CARDINALS) {
            Site potentialEnemy = gameMap.getSite(targetLoc, d2);
            if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
              possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength);
            }
          }
          if (possibleEnemyDamage > highestPossibleEnemyDamage) {
            enemyDirection = d;
            highestPossibleEnemyDamage = possibleEnemyDamage;
          }
        }
      }

      if (enemyDirection != Direction.STILL) {
        outerRing.put(l, enemyDirection);
      } else {
        outerRing.put(l, prodRatioDirection);
      }
    }
  }

  private static void generateInnerAreaDiffusionMap() {
    Direction[] SHUFFLED_CARDINALS = Direction.CARDINALS.clone();
    Collections.shuffle(Arrays.asList(SHUFFLED_CARDINALS));
    for (Direction d : SHUFFLED_CARDINALS) {
      for (Location l : outerRing.keySet()) { // Should technically use non-owner ring
        Location newLoc = gameMap.getLocation(l, d);
        Site newSite = gameMap.getSite(newLoc);
        int newDist = 1;
        while (innerArea.containsKey(newLoc)) {
          if (newDist < innerArea.get(newLoc).distToOuterRing && 
              newSite.strength > 5*newSite.production) {
            innerArea.put(newLoc, 
                new InnerAreaMemory(Direction.reverseDirection(d), newDist));
          }
          newDist++;
          newLoc = gameMap.getLocation(newLoc, d);
          newSite = gameMap.getSite(newLoc);
        }
      }
    }
  }
}
