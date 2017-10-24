package picture;

public class Colour {
  public static final Colour RED = new Colour(255, 0, 0);
  public static final Colour BLUE = new Colour(0, 0, 255);
  public static final Colour GREEN = new Colour(0, 255, 0);
  public static final Colour WHITE = new Colour(255, 255, 255);
  public static final Colour BLACK = new Colour(0, 0, 0);

  private final int red;
  private final int green;
  private final int blue;

  Colour(int red, int green, int blue) {
    this.red = red;
    this.green = green;
    this.blue = blue;
  }

  public int getRed() {
    return red;
  }

  public int getGreen() {
    return green;
  }

  public int getBlue() {
    return blue;
  }


  /**
   * See the overloaded version of the function for more information
   */
  public static Colour getAverage(Coordinate topLeft,
                                  Coordinate bottomRight, Picture picture) {
    return getAverage(topLeft, bottomRight, picture, 1);
  }

  /**
   * Calculates the average RGB color of N / (step * step) pixels between the two coordinates,
   * where N is the total amount of pixels in that region
   * @param topLeft one of the two coordinates
   * @param bottomRight one of the two coordinates
   * @param picture the picture from where the average is computed
   * @param step determines how many pixels are computed, bigger step results in faster computation
   *             times but slightly lower quality
   *             a value of 1 means ALL pixels are computed
   * @return average colour
   */
  static Colour getAverage(Coordinate topLeft,
                                  Coordinate bottomRight, Picture picture, int step) {
    long red = 0, green = 0, blue = 0, cnt = 0;
    for (int j = topLeft.getY(); j <= bottomRight.getY() - step + 1; j += step) {
      for (int i = topLeft.getX(); i <= bottomRight.getX() - step + 1; i += step) {
        red += picture.getPixel(i, j).getRed();
        green += picture.getPixel(i, j).getGreen();
        blue += picture.getPixel(i, j).getBlue();
        cnt++;
      }
    }
    if (cnt == 0) {
      return new Colour(0, 0, 0);
    }
    return new Colour((int) (red / cnt), (int) (green / cnt), (int) (blue / cnt));
  }

}
