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
// v8c: if 255 behind outerRing, go.
// v8d: look further when picking direction to fight
// v9: implemented the ben spector opening
// v10: repulsion map
// v11: 1-2 step expansion lookahead
// v11b: if noMansLand prod/str ratio < 0.2, use diffusion
// v12: aggrobot works?
// v13: added early game combat (enlisting), tuned down aggressivity, changed the enemy targeting calculationg
// v13b: parameter tuning: aggressivity, 

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
import java.util.Comparator;

// TODO: if winning in production, wait for strength?
// TODO: Waiting for opponent to take neutral: low priority because it doesn't happen very often

@SuppressWarnings("unchecked")
public class OldBot13b {
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
  static Map<Location, Direction> repulsionMap = new HashMap();
  static Map<Location, InnerAreaMemory> enlisted = new HashMap();
  static int noMansLandTotalStrg = 0;
  static int noMansLandTotalProd = 0;
  static boolean engagedEnemy = false;
  static boolean expansionMode = true;
  static PrintWriter log;

	public static void main(String[] args) throws Exception {
    boolean isFirstCycle = true;
    int turnNumber = 0;
		iPackage = Networking.getInit();
		myID = iPackage.myID;
		gameMap = iPackage.map;
    log = new PrintWriter("./log.txt", "UTF-8");

		Networking.sendInit("OldBot13b");

		while(true) {
      // Cycle variables
      turnNumber++;
			ArrayList<Move> moves = new ArrayList<Move>();
      ownerProduction.clear();
      ownerStrength.clear();
      ownerTerritory.clear();
      outerRing.clear();
      noMansLand.clear();
      innerArea.clear();
      repulsionMap.clear();
      enlisted.clear();
      noMansLandTotalProd = 0;
      noMansLandTotalStrg = 0;
			gameMap = Networking.getFrame();

      if (isFirstCycle) {
        initiate(); isFirstCycle = false;
      }

      initialSweep(gameMap);

      outerRingStrategy();

      // Modes
      if (expansionMode && (double)noMansLandTotalProd / (noMansLandTotalStrg+1) < 0.2) {
        greedyExpand();
        for (Location l : innerArea.keySet()) {
          moves.add(new Move(l, innerArea.get(l).dir));
        }
        Networking.sendFrame(moves); continue;
      }
      else {
        generateInnerAreaDiffusionMap(1);
        enlistCombat();
        generateRepulsionMap();
        resolveEnsembleMap();
        resolveCollisions();
        for (Location l : outerRing.keySet()) {
          moves.add(new Move(l, outerRing.get(l).dir));
        }
        for (Location l : innerArea.keySet()) {
          moves.add(new Move(l, innerArea.get(l).dir));
        }
        Networking.sendFrame(moves); continue;
      }
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

  private static double computeTwoStepBestProdOverStr(Location l) {
    double bestProdOverStr = 0.0;
    Site s = gameMap.getSite(l);
    for (Direction d : Direction.CARDINALS) {
      Site ss = gameMap.getSite(l, d);
      if (ss.owner == 0) {
        for (Direction dd : Direction.CARDINALS) {
          Site sss = gameMap.getSite(gameMap.getLocation(l, d), dd);
          if (dd != d && sss.owner == 0) {
            double prodOverStr = (double)(s.production + ss.production + sss.production) / (s.strength + ss.strength + sss.strength + s.production + ss.production + 1);
            if (prodOverStr > bestProdOverStr) {
              bestProdOverStr = prodOverStr;
            }
          }
        }
      }
    }
    return bestProdOverStr;
  }

  private static double computeOneStepBestProdOverStr(Location l) {
    double bestProdOverStr = 0.0;
    Site s = gameMap.getSite(l);
    for (Direction d : Direction.CARDINALS) {
      Site ss = gameMap.getSite(l, d);
      if (ss.owner == 0) {
        double prodOverStr = (double)(s.production + ss.production) / (s.strength + ss.strength + s.production + 1);
        if (prodOverStr > bestProdOverStr) {
          bestProdOverStr = prodOverStr;
        }
      }
    }
    return bestProdOverStr;
  }

  private static void greedyExpand() {
    int MAX_DEPTH = 10;
    for (Location l : outerRing.keySet()) {
      innerArea.put(l, new InnerAreaMemory(Direction.STILL, Integer.MAX_VALUE));
    }

    ArrayList<Location> sortedNoMansLand = new ArrayList<Location>(noMansLand.keySet());
    Collections.sort(sortedNoMansLand, new Comparator<Location>() {
      public int compare(Location l1, Location l2) {
        Site s1 = gameMap.getSite(l1);
        Site s2 = gameMap.getSite(l2);
        return new Double(computeTwoStepBestProdOverStr(l2)).compareTo(computeTwoStepBestProdOverStr(l1));
      }
    });

    Set<Location> used = new HashSet();
    for (Location l : sortedNoMansLand) {
      Site targetSite = gameMap.getSite(l);
      int targetStrg = targetSite.strength;
      Deque<LocNode> toVisitQueue = new LinkedList();
      for (Direction d : Direction.CARDINALS) {
        Location nextLoc = gameMap.getLocation(l, d);
        if (innerArea.containsKey(nextLoc) && !used.contains(nextLoc)) {
          Set<Location> hs = new HashSet(); hs.add(nextLoc);
          toVisitQueue.add(new LocNode(nextLoc, hs, targetStrg, 0, 1, d));
        }
      }
      while (!toVisitQueue.isEmpty()) {
        LocNode cur = toVisitQueue.removeFirst();
        Site curSite = gameMap.getSite(cur.loc);
        if (cur.targetStrg < curSite.strength) { // backtrack
          innerArea.put(cur.loc, new InnerAreaMemory(Direction.reverseDirection(cur.lastDirection), cur.depth));
          for (Location visited : cur.visited) {
            used.add(visited);
          }
          break;
        }
        else { // We must go deeper
          for (Direction d : Direction.CARDINALS) {
            Location nextLoc = gameMap.getLocation(cur.loc, d);
            if (innerArea.containsKey(nextLoc) && !used.contains(nextLoc) && !cur.visited.contains(nextLoc) && cur.depth < MAX_DEPTH) {
              cur.visited.add(nextLoc);
              toVisitQueue.add(new LocNode(
                    nextLoc, 
                    cur.visited, 
                    targetStrg - (cur.pathProductionSum + gameMap.getSite(cur.loc).strength),
                    cur.pathProductionSum + gameMap.getSite(cur.loc).production,
                    cur.depth + 1,
                    d));
            }
          }
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

        // Find outer ring, inner area, and noMansLand
        if (site.owner == myID) {
          for (Direction d : Direction.CARDINALS) {
            if (gameMap.getSite(loc, d).owner != myID) {
              outerRing.put(loc, new OuterRingMemory(Direction.STILL, false));

              Location noMansLoc = gameMap.getLocation(loc, d);
              Site noMansSite = gameMap.getSite(loc, d);
              noMansLand.put(noMansLoc, Direction.STILL);
              noMansLandTotalStrg += noMansSite.strength;
              noMansLandTotalProd += noMansSite.production;
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
      if (outerRing.containsKey(l) && outerRing.get(l).dir == Direction.STILL ||
          innerArea.containsKey(l) && innerArea.get(l).dir == Direction.STILL) {
        currentStrength = s.strength + s.production;
      }
      for (Direction d : Direction.CARDINALS) {
        Location checkloc = gameMap.getLocation(l, d);
        Site checksite = gameMap.getSite(checkloc);
        if (innerArea.containsKey(checkloc) && innerArea.get(checkloc).dir == Direction.reverseDirection(d)) {
          if ((double)(Math.min(255, currentStrength + checksite.strength) - currentStrength) / (checksite.strength+1) > MOVE_EFFICIENCY_MINIMUM) {
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
          if ((double)(Math.min(255, currentStrength + checksite.strength) - currentStrength) / (checksite.strength+1) > MOVE_EFFICIENCY_MINIMUM) {
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
      boolean isNextToInner255 = false;
      boolean isFighting = false;
      for (Direction d : Direction.CARDINALS) {
        Location targetLoc = gameMap.getLocation(l, d);
        Site targetSite = gameMap.getSite(l, d);
        if (innerArea.containsKey(targetLoc) && targetSite.strength == 255) {
          isNextToInner255 = true;
          break;
        }
      }
      for (Direction d : Direction.CARDINALS) {
        Location targetLoc = gameMap.getLocation(l, d);
        Site target = gameMap.getSite(targetLoc);

        // Target highest accessible prod ratio
        boolean moveCondition = (current.strength > target.strength) || isNextToInner255;
        if (target.owner != myID && moveCondition) {
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
          // Begin triangle calc
          Site potentialEnemy;
          isFighting = true;
          potentialEnemy = gameMap.getSite(targetLoc, d);
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength + potentialEnemy.production) * 2;
          }
          potentialEnemy = gameMap.getSite(targetLoc, Direction.turnLeft(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength + potentialEnemy.production) * 2;
          }
          potentialEnemy = gameMap.getSite(targetLoc, Direction.turnRight(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength + potentialEnemy.production) * 2;
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, d), d);
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength) * 1;
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, d), Direction.turnLeft(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength) * 2;
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, d), Direction.turnRight(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength) * 2;
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, Direction.turnLeft(d)), Direction.turnLeft(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength) * 1;
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, Direction.turnRight(d)), Direction.turnRight(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += Math.min(current.strength, potentialEnemy.strength) * 1;
          }
          // End triangle calc
          
          if (possibleEnemyDamage > highestPossibleEnemyDamage) {
            enemyDirection = d;
            highestPossibleEnemyDamage = possibleEnemyDamage;
          }
        }
      }

      if (enemyDirection != Direction.STILL) {
        outerRing.put(l, new OuterRingMemory(enemyDirection, isFighting));
        engagedEnemy = true;
        expansionMode = false;
      } else {
        outerRing.put(l, new OuterRingMemory(prodRatioDirection, isFighting));
      }
    }
  }

  private static void generateInnerAreaDiffusionMap(int aggressivity) {
    Direction[] SHUFFLED_CARDINALS = Direction.CARDINALS.clone();
    Collections.shuffle(Arrays.asList(SHUFFLED_CARDINALS));
    for (Direction d : SHUFFLED_CARDINALS) {
      for (Location l : noMansLand.keySet()) {
        Site s = gameMap.getSite(l);
        Location newLoc = gameMap.getLocation(l, d);
        Site newSite = gameMap.getSite(newLoc);
        int newDist = 1 + (aggressivity == 3 ? s.strength / (s.production + 1) : 
                           aggressivity == 2 ? s.strength / (2*s.production + 1) : 
                           aggressivity == 1 ? s.strength / (3*s.production + 1) : 
                           0);
        while (innerArea.containsKey(newLoc) || outerRing.containsKey(newLoc)) {
          // if outer ring is fighting, send more this way!
//        if (outerRing.containsKey(newLoc) && outerRing.get(newLoc).isFighting) {
//          newDist -= 2;
//        }
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

  private static void enlistCombat() {
    for (Location l : outerRing.keySet()) {
      if (outerRing.get(l).isFighting) {
        int strengthToEnlist = 255;
        Deque<Move> toEnlist = new LinkedList();
        Set<Location> visited = new HashSet();
        for (Direction d : Direction.CARDINALS) {
          Location next = gameMap.getLocation(l, d);
          if (!visited.contains(next) && (innerArea.containsKey(next) || (outerRing.containsKey(next) && !outerRing.get(next).isFighting))) {
            toEnlist.add(new Move(next, Direction.reverseDirection(d)));
          }
        }
        while (!toEnlist.isEmpty()) {
          Move v = toEnlist.removeFirst();
          Site vSite = gameMap.getSite(v.loc);
          strengthToEnlist -= vSite.strength;
          int depth = enlisted.containsKey(gameMap.getLocation(v.loc, v.dir)) ? enlisted.get(gameMap.getLocation(v.loc, v.dir)).distToOuterRing : 0;
          if (vSite.strength > 5*vSite.production &&
              (!enlisted.containsKey(v.loc) || enlisted.get(v.loc).distToOuterRing < depth+1)) {
            enlisted.put(v.loc, new InnerAreaMemory(v.dir, depth + 1));
          }
          if (strengthToEnlist <= 0) {
            break;
          }
          visited.add(v.loc);
          for (Direction d : Direction.CARDINALS) {
            Location next = gameMap.getLocation(v.loc, d);
            if (!visited.contains(next) && (innerArea.containsKey(next) || (outerRing.containsKey(next) && !outerRing.get(next).isFighting))) {
              toEnlist.add(new Move(next, Direction.reverseDirection(d)));
            }
          }
        }
      }
    }
    for (Location l : enlisted.keySet()) {
      if (outerRing.containsKey(l)) {
        outerRing.put(l, new OuterRingMemory(enlisted.get(l).dir, false));
      }
      else if (innerArea.containsKey(l)) {
        innerArea.put(l, new InnerAreaMemory(enlisted.get(l).dir, innerArea.get(l).distToOuterRing));
      }
    }
  }
  
  private static void generateRepulsionMap() {
    for (Location l : innerArea.keySet()) {
      Site s = gameMap.getSite(l);
      int repulsion_x = 0;
      int repulsion_y = 0;
      Site neighborSite = gameMap.getSite(l, Direction.NORTH);
      repulsion_y -= Math.min(s.strength, neighborSite.strength);
      neighborSite = gameMap.getSite(l, Direction.SOUTH);
      repulsion_y += Math.min(s.strength, neighborSite.strength);
      neighborSite = gameMap.getSite(l, Direction.EAST);
      repulsion_x -= Math.min(s.strength, neighborSite.strength);
      neighborSite = gameMap.getSite(l, Direction.WEST);
      repulsion_x += Math.min(s.strength, neighborSite.strength);

      if (Math.abs(repulsion_x) >= Math.max(Math.abs(repulsion_y), 5*s.production)) {
        repulsionMap.put(l, repulsion_x > 0 ? Direction.EAST : Direction.WEST);
      }
      else if (Math.abs(repulsion_y) > Math.max(Math.abs(repulsion_x), 5*s.production)) {
        repulsionMap.put(l, repulsion_y > 0 ? Direction.NORTH : Direction.SOUTH);
      }
    }
  }

  private static void resolveEnsembleMap() {
    for (Location l : innerArea.keySet()) {
      if (repulsionMap.containsKey(l) && 
          innerArea.get(l).dir == Direction.reverseDirection(repulsionMap.get(l)) &&
          innerArea.get(l).distToOuterRing < 1
          ) {
        innerArea.put(l, new InnerAreaMemory(Direction.STILL, innerArea.get(l).distToOuterRing));
      }
    }
  }

}
