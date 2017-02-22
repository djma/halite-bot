// Changelog:
// v1: refactor from python
// v2: targeting neutral square to do most damage possible
// v3: fixed diffusion algo to make less collisions
// v4: optimized the growth period
// v5: bias diffusion units towards battles, param optimized
// v6: collision resolution v1
// v7: realized that collisions are capped before fighting damage is done. Improved collision resolution to v2
// v8: welp.. looks like resolving inner collisions are good too
// v8b: refactored code

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.io.PrintWriter;

// TODO: if winning in production, wait for strength?
// TODO: Waiting for opponent to take neutral: low priority because it doesn't happen very often

@SuppressWarnings("unchecked")
public class OldBot8b {
  // Global variables
  static GameMap gameMap;
  static InitPackage iPackage;
  static final int NEUTRAL = 0;
  static final double MOVE_EFFICIENCY_MINIMUM = 0.8;
  static int myID;

  static Map<Integer, Integer> ownerProduction = new HashMap();
  static Map<Integer, Integer> ownerStrength = new HashMap();
  static Map<Integer, Integer> ownerTerritory = new HashMap();
  static Map<Location, OuterRingMemory> outerRing = new HashMap();
  static Map<Location, Direction> noMansLand = new HashMap();
  static Map<Location, InnerAreaMemory> innerArea = new HashMap();
  static boolean engagedEnemy = false;
  static PrintWriter log;

	public static void main(String[] args) throws Exception {
    boolean isFirstCycle = true;
		iPackage = Networking.getInit();
		myID = iPackage.myID;
		gameMap = iPackage.map;
    log = new PrintWriter("./log.txt", "UTF-8");

		Networking.sendInit("OldBot8b");

		while(true) {
      // Cycle variables
			ArrayList<Move> moves = new ArrayList<Move>();
      ownerProduction.clear();
      ownerStrength.clear();
      ownerTerritory.clear();
      outerRing.clear();
      noMansLand.clear();
      innerArea.clear();
			gameMap = Networking.getFrame();

      if (isFirstCycle) {
        initiate(); isFirstCycle = false;
      }

      initialSweep(gameMap);

      outerRingStrategy();

      generateInnerAreaDiffusionMap();

//    if (!engagedEnemy) {
//      neutralDoubleTeaming(); 
//    }
  
      resolveCollisions();

      for (Location l : outerRing.keySet()) {
        moves.add(new Move(l, outerRing.get(l).dir));
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

    int bestProd = 0;

    Direction d1 = Direction.NORTH;
    Direction d2 = Direction.EAST;
//  Deque<Direction> pathStack = new LinkedList(); // DFS
//  pathStack.addFirst(d1); pathStack.addFirst(d2);
//  int currentProd = 0;
//  int currentStrg = gameMap.getSite(initialLoc).strength;
//  Location currentLoc = initialLoc;
//  while (!pathStack.isEmpty()) {
//    Direction nextDir = pathStack.removeFirst();
//    Location nextLoc = gameMap.getLocation(currentLoc, nextDir);
//    Site nextSite = gameMap.getSite(nextLoc);
//    if (currentStrg > nextSite.strength) {
//      pathStack.addFirst(d1); pathStack.addFirst(d2);
//      currentStrg -= nextSite.strength;
//    }
//  }

  }

//private static computeBestNeutralPath(Location from, int strgLeft, Direction d1, Direction d2) {

//}

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

        // Find outer ring, inner area, and noMansLand
        if (site.owner == myID) {
          for (Direction d : Direction.CARDINALS) {
            if (gameMap.getSite(loc, d).owner != myID) {
              outerRing.put(loc, new OuterRingMemory(Direction.STILL, false));
              noMansLand.put(gameMap.getLocation(loc, d), Direction.STILL);
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

  private static void neutralDoubleTeaming() {
    for (Location l : noMansLand.keySet()) {
      int stillStrengthSum = 0;
      boolean isEnemyAdjacent = false;
      for (Direction d : Direction.CARDINALS) {
        Location nextLoc = gameMap.getLocation(l, d);
        Site nextSite = gameMap.getSite(nextLoc);
        if (nextSite.owner == myID && outerRing.get(nextLoc).dir == Direction.STILL) {
          stillStrengthSum += nextSite.strength;
        } else if (nextSite.owner != 0) {
          isEnemyAdjacent = true;
        }
      }
      if (!isEnemyAdjacent &&
          stillStrengthSum > gameMap.getSite(l).strength && 
          stillStrengthSum < 1.2 * gameMap.getSite(l).strength) {
        for (Direction d : Direction.CARDINALS) {
          Location nextLoc = gameMap.getLocation(l, d);
          Site nextSite = gameMap.getSite(nextLoc);
          if (nextSite.owner == myID && outerRing.get(nextLoc).dir == Direction.STILL) {
            outerRing.put(nextLoc, new OuterRingMemory(Direction.reverseDirection(d), false));
          }
        }
      }
    }
  }

  private static void resolveCollisionsHelper(Deque<Location> toResolve) {
    while (!toResolve.isEmpty()) {
      Location l = toResolve.removeFirst();
      Site s = gameMap.getSite(l);
      int currentStrength = 0;
      if (outerRing.get(l) != null && outerRing.get(l).dir == Direction.STILL ||
          innerArea.get(l) != null && innerArea.get(l).dir == Direction.STILL) {
        currentStrength = s.strength + s.production;
      }
      for (Direction d : Direction.CARDINALS) {
        Location checkloc = gameMap.getLocation(l, d);
        Site checksite = gameMap.getSite(checkloc);
        if (innerArea.containsKey(checkloc) && innerArea.get(checkloc).dir == Direction.reverseDirection(d)) {
          if ((double)(Math.min(255, currentStrength + checksite.strength) - currentStrength) / checksite.strength > MOVE_EFFICIENCY_MINIMUM) {
            currentStrength += checksite.strength;
          }
          else {
            innerArea.put(checkloc, new InnerAreaMemory(Direction.STILL, innerArea.get(checkloc).distToOuterRing));
            toResolve.add(checkloc);
          }
        }
      }
    }
  }

  private static void resolveCollisions() {
    Deque<Location> toResolve = new LinkedList();
    for (Location l : noMansLand.keySet()) { 
      // I believe incoming strengths are combined and capped before they do damage to neutrals
      // Hence, the logic is almost the same as the outerRing
      Site s = gameMap.getSite(l);
      int currentStrength = 0;
      for (Direction d : Direction.CARDINALS) {
        Location checkloc = gameMap.getLocation(l, d);
        Site checksite = gameMap.getSite(checkloc);
        if (outerRing.containsKey(checkloc) && outerRing.get(checkloc).dir == Direction.reverseDirection(d)) {
          if ((double)(Math.min(255, currentStrength + checksite.strength) - currentStrength) / checksite.strength > MOVE_EFFICIENCY_MINIMUM) {
            currentStrength += checksite.strength;
          }
          else {
            outerRing.put(checkloc, new OuterRingMemory(Direction.STILL, outerRing.get(checkloc).isFighting));
          }
        }
      }
    }
    for (Location l : outerRing.keySet()) {
      toResolve.add(l);
    }
    resolveCollisionsHelper(toResolve);
    for (Location l : innerArea.keySet()) {
      toResolve.add(l);
    }
    resolveCollisionsHelper(toResolve);
  }

  private static void outerRingStrategy() {
    for (Location l : outerRing.keySet()) {
      double highestProdRatio = 0.0;
      Direction prodRatioDirection = Direction.STILL;
      Direction enemyDirection = Direction.STILL;
      int highestPossibleEnemyDamage = 0;
      int lowestStr = 256;
      Site current = gameMap.getSite(l);
      for (Direction d : Direction.CARDINALS) {
        Location targetLoc = gameMap.getLocation(l, d);
        Site target = gameMap.getSite(targetLoc);

        // Target highest accessible prod ratio
        if (target.owner != myID && current.strength > target.strength) {
          double targetProdRatio = (double)target.production / (target.strength+1) + 0.0001; // Take squares with zero production
          //double targetProdRatio = (double)target.production / (target.strength+current.production+1) + 0.0001; // Take squares with zero production
          if (targetProdRatio > highestProdRatio) {
            prodRatioDirection = d;
            highestProdRatio = (double)target.production / (target.strength+1);
          }
        }

        // Target enemy 
        // Code could be slightly faster here. Don't double check sites
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
        outerRing.put(l, new OuterRingMemory(enemyDirection, true));
        engagedEnemy = true;
      } else {
        outerRing.put(l, new OuterRingMemory(prodRatioDirection, false));
      }
    }
  }

  private static void generateInnerAreaDiffusionMap() {
    Direction[] SHUFFLED_CARDINALS = Direction.CARDINALS.clone();
    Collections.shuffle(Arrays.asList(SHUFFLED_CARDINALS));
    for (Direction d : SHUFFLED_CARDINALS) {
      for (Location l : noMansLand.keySet()) {
        Location newLoc = gameMap.getLocation(l, d);
        Site newSite = gameMap.getSite(newLoc);
        int newDist = 1;
        while (innerArea.containsKey(newLoc) || outerRing.containsKey(newLoc)) {
          // if outer ring is fighting, send more this way!
          if (outerRing.containsKey(newLoc) && outerRing.get(newLoc).isFighting) {
            newDist -= 2;
          }

          if (innerArea.containsKey(newLoc) &&
              newDist < innerArea.get(newLoc).distToOuterRing && 
              newSite.strength >= 5*newSite.production) {
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
