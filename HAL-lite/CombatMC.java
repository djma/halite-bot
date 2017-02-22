import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.Collections;
import java.util.Comparator;
import java.io.PrintWriter;

@SuppressWarnings("unchecked")
public class CombatMC {
  int NUM_GENERATIONS = 50;
  GameMap gameMap;
  GameMap interp1GameMap;
  GameMap interp2GameMap;
  GameMap nextGameMap;
  Set<Location> p1locs = new HashSet();
  Set<Location> p2locs = new HashSet();
  int myID;
  Random rng = new Random();
  PrintWriter log;

  public CombatMC(GameMap gameMap_, int myID_, PrintWriter log_) throws Exception {
    log = log_;
    gameMap = gameMap_;
    myID = myID_;
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location l = new Location(x, y);
        Site s = gameMap.getSite(l);
        // Find move domain
        if (s.owner == 0 && s.strength == 0) {
          for (Direction d : Direction.CARDINALS) {
            Location ll = gameMap.getLocation(l, d);
            Site ss = gameMap.getSite(ll);
            if (ss.owner == myID && ss.strength > 0) {
              p1locs.add(new Location(ll));
              for (Direction dd : Direction.CARDINALS) {
                Location lll = gameMap.getLocation(ll, dd);
                Site sss = gameMap.getSite(lll);
                if (sss.owner == myID && sss.strength > 0) {
                  p1locs.add(new Location(lll));
                }
              }
            }
            else if (ss.owner != 0 && ss.strength > 0) {
              p2locs.add(new Location(ll));
              for (Direction dd : Direction.CARDINALS) {
                Location lll = gameMap.getLocation(ll, dd);
                Site sss = gameMap.getSite(new Location(lll));
                if (sss.owner != 0 && sss.strength > 0) {
                  p2locs.add(new Location(lll));
                }
              }
            }
          }
        }
      }
    }
  }

  private void createNextGameMap(Map<Location, Direction> p1_moves, Map<Location, Direction> p2_moves) {
    // Reset states
    interp1GameMap = new GameMap(gameMap.width, gameMap.height);
    interp2GameMap = new GameMap(gameMap.width, gameMap.height);
    nextGameMap = new GameMap(gameMap.width, gameMap.height);

    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location l = new Location(x, y);
        Site s = gameMap.getSite(l);
        int prod = s.production;
        int strength = s.strength;
        int owner = s.owner;
        interp1GameMap.getSite(l).strength = 0;
        interp2GameMap.getSite(l).strength = 0;
        nextGameMap.getSite(l).production = prod;
        nextGameMap.getSite(l).strength = strength;
        nextGameMap.getSite(l).owner = owner;
      }
    }

    // Make still moves
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location l = new Location(x,y);
        Site s = gameMap.getSite(l);
        int owner = s.owner;
        int strength = s.strength;
        int production = s.production;
        if (owner == myID) {
          interp1GameMap.getSite(l).strength = strength + production;
        }
        else if (owner != 0) {
          interp2GameMap.getSite(l).strength = strength + production;
        }
      }
    }

    // Move pieces
    for (Location l : p1_moves.keySet()) {
      Direction d = p1_moves.get(l);
      if (d != Direction.STILL) {
        Location fromLoc = l;
        Site fromSite = gameMap.getSite(l);
        Location toLoc = gameMap.getLocation(l, d);
        Site toSite = gameMap.getSite(toLoc);
        interp1GameMap.getSite(fromLoc).strength -= fromSite.strength + fromSite.production;
        interp1GameMap.getSite(toLoc).strength += fromSite.strength;
      }
    }
    for (Location l : p2_moves.keySet()) {
      Direction d = p2_moves.get(l);
      if (d != Direction.STILL) {
        Location fromLoc = l;
        Site fromSite = gameMap.getSite(l);
        Location toLoc = gameMap.getLocation(l, d);
        Site toSite = gameMap.getSite(toLoc);
        interp2GameMap.getSite(fromLoc).strength -= fromSite.strength + fromSite.production;
        interp2GameMap.getSite(toLoc).strength += fromSite.strength;
      }
    }

    // Cap strengths
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location l = new Location(x,y);
        Site s = gameMap.getSite(l);
        interp1GameMap.getSite(l).strength = Math.min(255, interp1GameMap.getSite(l).strength);
        interp2GameMap.getSite(l).strength = Math.min(255, interp2GameMap.getSite(l).strength);
      }
    }

    // Create next map
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location l = new Location(x,y);
        int initp1strg = interp1GameMap.getSite(l).strength;
        int initp2strg = interp2GameMap.getSite(l).strength;
        int p1strg = interp1GameMap.getSite(l).strength;
        int p2strg = interp2GameMap.getSite(l).strength;
        for (Direction d : Direction.DIRECTIONS) {
          Location ll = gameMap.getLocation(l, d);
          if (d == Direction.STILL && gameMap.getSite(ll).owner == 0) {
            p1strg -= gameMap.getSite(ll).strength;
            p2strg -= gameMap.getSite(ll).strength;
          }
          p1strg -= interp2GameMap.getSite(ll).strength;
          p2strg -= interp1GameMap.getSite(ll).strength;
        }

        Site newSite = nextGameMap.getSite(l);
        if (p1strg > 0) {
          newSite.strength = p1strg;
          newSite.owner = myID;
        }
        else if (p2strg > 0) {
          newSite.strength = p2strg;
          newSite.owner = 696969; // "not my id"
        }
        else if (initp1strg == 0 && gameMap.getSite(l).owner == myID) {
          newSite.strength = 0;
          newSite.owner = myID;
        }
        else if (initp2strg == 0 && gameMap.getSite(l).owner != 0 && gameMap.getSite(l).owner != myID) {
          newSite.strength = 0;
          newSite.owner = 696969;
        }
        else {
          newSite.strength = 0;
          newSite.owner = 0;
        }
      }
    }
    //log.println("number of moves: " + p1_moves.size());
    for (Location l : p1_moves.keySet()) {
      //log.print("" + l + " " + p1_moves.get(l) + ", ");
    }
    //log.print("\n");
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        int str = nextGameMap.getSite(new Location(x,y)).strength;
        //log.print("" + (str < 10 ? "  " : str < 100 ? " " : "") + str + " ");
      }
      //log.print("\n");
    }
    //log.print("\n");
    log.flush();
  }

  private int evalGameMap(GameMap gm) {
    int p1score = 0;
    int p2score = 0;
    for(int y = 0; y < gm.height; y++) {
      for(int x = 0; x < gm.width; x++) {
        Location loc = new Location(x, y);
        Site site = gm.getSite(loc);
        if (site.owner == myID) {
          p1score += site.strength + site.production;
        }
        else if (site.owner != 0) {
          p2score += site.strength + site.production;
        }
      }
    }
    return p1score - p2score;
  }

  public Map<Location, Direction> generateP1Moves() {
    // Generate 100 p2moves, might have duplicates :(
    final Map<Map<Location, Direction>, Integer> p2moves = new HashMap();;
    for (int i = 0; i < 100; ++i) {
      Map<Location, Direction> move = new HashMap();
      for (Location l : p2locs) {
        move.put(l, Direction.randomDirection());
      }
      p2moves.put(move, Integer.MIN_VALUE);
    }

    // optimize p2
    for (int k = 0; k < NUM_GENERATIONS; ++k) {
      // evaluate moves
      for (Map<Location, Direction> move : p2moves.keySet()) {
        if (p2moves.get(move) == Integer.MIN_VALUE) {
          createNextGameMap(new HashMap(), move);
          p2moves.put(move, evalGameMap(nextGameMap));
        }
      }
      // sort moves
      ArrayList<Map<Location, Direction>> sortedP2Moves = new ArrayList<Map<Location, Direction>>(p2moves.keySet());
      Collections.sort(sortedP2Moves, new Comparator<Map<Location, Direction>>() {
        public int compare(Map<Location, Direction> m1, Map<Location, Direction> m2) {
          return p2moves.get(m2).compareTo(p2moves.get(m1));
        }
      });
      // Mate mutate
      for (int i = sortedP2Moves.size()/4+1; i < sortedP2Moves.size(); ++i) { // Kill 75%
        p2moves.remove(sortedP2Moves.get(i));
        Map<Location, Direction> move = new HashMap();
        int father = rng.nextInt(sortedP2Moves.size()/4+1);
        int mother = rng.nextInt(sortedP2Moves.size()/4+1);
        for (Location l : p2locs) {
          move.put(l, rng.nextInt(1) == 0 ? sortedP2Moves.get(father).get(l) : sortedP2Moves.get(mother).get(l)); // Mate
          if (rng.nextDouble() < 0.1) {
            move.put(l, Direction.randomDirection()); // Mutate
          }
        }
        p2moves.put(move, Integer.MIN_VALUE);
      }
    }
    // evaluate moves // REFACTOR ME PLEASE
    for (Map<Location, Direction> move : p2moves.keySet()) {
      if (p2moves.get(move) == Integer.MIN_VALUE) {
        createNextGameMap(new HashMap(), move);
        p2moves.put(move, evalGameMap(nextGameMap));
      }
    }
    // sort moves // REFACTOR ME PLEASE
    ArrayList<Map<Location, Direction>> sortedP2Moves = new ArrayList<Map<Location, Direction>>(p2moves.keySet());
    Collections.sort(sortedP2Moves, new Comparator<Map<Location, Direction>>() {
      public int compare(Map<Location, Direction> m1, Map<Location, Direction> m2) {
        return p2moves.get(m2).compareTo(p2moves.get(m1));
      }
    });
    Map<Location, Direction> bestp2move = sortedP2Moves.get(0);
    
    // Generate 100 p1moves
    final Map<Map<Location, Direction>, Integer> p1moves = new HashMap();;
    for (int i = 0; i < 100; ++i) {
      Map<Location, Direction> move = new HashMap();
      for (Location l : p1locs) {
        move.put(l, Direction.randomDirection());
      }
      p1moves.put(move, Integer.MIN_VALUE);
    }

    //log.println("number of movesets: " + p1moves.size());
    // optimize p1
    for (int k = 0; k < NUM_GENERATIONS; ++k) {
      // evaluate moves
      for (Map<Location, Direction> move : p1moves.keySet()) {
        if (p1moves.get(move) == Integer.MIN_VALUE) {
          createNextGameMap(move, bestp2move);
          int mapScore = evalGameMap(nextGameMap);
          //log.println("mapScore: " + mapScore);
          p1moves.put(move, mapScore);
        }
      }
      // sort moves
      ArrayList<Map<Location, Direction>> sortedP1Moves = new ArrayList<Map<Location, Direction>>(p1moves.keySet());
      Collections.sort(sortedP1Moves, new Comparator<Map<Location, Direction>>() {
        public int compare(Map<Location, Direction> m1, Map<Location, Direction> m2) {
          return p1moves.get(m2).compareTo(p1moves.get(m1));
        }
      });
      // Mate mutate
      for (int i = sortedP1Moves.size()/4+1; i < sortedP1Moves.size(); ++i) { // Kill 75%
        p1moves.remove(sortedP1Moves.get(i));
        Map<Location, Direction> move = new HashMap();
        int father = rng.nextInt(sortedP1Moves.size()/4+1);
        int mother = rng.nextInt(sortedP1Moves.size()/4+1);
        for (Location l : p1locs) {
          move.put(l, rng.nextInt(1) == 0 ? sortedP1Moves.get(father).get(l) : sortedP1Moves.get(mother).get(l)); // Mate
          if (rng.nextDouble() < 0.1) {
            move.put(l, Direction.randomDirection()); // Mutate
          }
        }
        p1moves.put(move, Integer.MIN_VALUE);
      }
    }
    // evaluate moves // REFACTOR ME PLEASE
    for (Map<Location, Direction> move : p1moves.keySet()) {
      if (p1moves.get(move) == Integer.MIN_VALUE) {
        createNextGameMap(move, bestp2move);
        p1moves.put(move, evalGameMap(nextGameMap));
      }
    }
    // sort moves // REFACTOR ME PLEASE
    ArrayList<Map<Location, Direction>> sortedP1Moves = new ArrayList<Map<Location, Direction>>(p1moves.keySet());
    Collections.sort(sortedP1Moves, new Comparator<Map<Location, Direction>>() {
      public int compare(Map<Location, Direction> m1, Map<Location, Direction> m2) {
        return p1moves.get(m2).compareTo(p1moves.get(m1));
      }
    });
    Map<Location, Direction> bestp1move = sortedP1Moves.get(0);

    // return best move set
    for (Location l : bestp1move.keySet()) {
      //log.print("Best Move: " + l + " " + bestp1move.get(l) + ", ");
    }
    //log.println("\nBest Score: " + p1moves.get(bestp1move));
    return bestp1move;
  }
}
