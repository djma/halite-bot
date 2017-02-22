public class Location {
  public int x, y;

  public Location(int x_, int y_) {
    x = x_;
    y = y_;
  }
  public Location(Location l) {
  	x = l.x;
  	y = l.y;
  }
  public String toString() {
    return "" + x + "," + y;
  }

  @Override
  public int hashCode() {
    return x*1000 + y;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Location other = (Location) obj;
    return x == other.x && y == other.y;
  }
}
