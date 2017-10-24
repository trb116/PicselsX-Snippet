package picture;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import picsels.FileUtils;
import picsels.PictureProcessingActivity;
import picsels.R;

import java.util.ArrayList;
import java.util.List;


public class Process {
  private final static int CANVAS_PREFERRED_WIDTH = 1500;
  final static int NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors();

  private final int oldWidth;
  private final int oldHeight;
  private final int scalar;
  final int newWidth;
  final int newHeight;
  final int tileSize;

  private Picture originalPicture;
  private Picture resizedPicture;
  private ImageView view;
  AppCompatActivity activity;
  List<Picture> pictureList;
  Settings settings;
  Canvas canvas;
  List<Colour> pictureAverages;
  List<Colour> quadrants;


  Process(Settings settings, AppCompatActivity activity) {
    this.activity = activity;
    originalPicture = FileUtils.loadPictureFromURI(settings.getMainPictureURI(), activity);

    while (view == null) {
      view = (ImageView) activity.findViewById(R.id.imageView);
    }

    oldWidth = originalPicture.getWidth();
    oldHeight = originalPicture.getHeight();

    this.settings = settings;
    scalar = settings.getScalar();

    newWidth = oldWidth * scalar;
    newHeight = oldHeight * scalar;

    tileSize = newWidth / settings.getPowerIndex() / scalar * scalar;

    resizedPicture = Utils.getResize(originalPicture, CANVAS_PREFERRED_WIDTH,
        CANVAS_PREFERRED_WIDTH * oldHeight / oldWidth);

    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        view.setImageBitmap(resizedPicture.getBitmap());
      }
    });

    quadrants = getQuadrantsConcurrently();

    originalPicture = Utils.getResize(originalPicture, newWidth, newHeight);
    pictureList = FileUtils.loadResizedAssets(null, activity, tileSize, tileSize);

    canvas = new Canvas(originalPicture.getBitmap());
    canvas.drawBitmap(originalPicture.getBitmap(), 0, 0, null);

    resolvePainterType();
  }

  /**
   * transforms the picture associated with this process
   *
   * @return new picture representing a mosaic of the component pictures
   */

  public Picture transform() {
    pictureAverages = new ArrayList<>();
    for (Picture p : pictureList) {
      pictureAverages.add(Colour.getAverage(new Coordinate(),
          new Coordinate(p.getWidth() - 1, p.getHeight() - 1), p, Settings.QUADRANTS_STEP));
    }

    merge();

    pictureList = null;
    quadrants = null;
    pictureAverages = null;
    System.gc();

    resizedPicture = Utils.getResize(originalPicture, CANVAS_PREFERRED_WIDTH,
        CANVAS_PREFERRED_WIDTH * oldHeight / oldWidth);
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        view.setImageBitmap(resizedPicture.getBitmap());
      }
    });

    return originalPicture;
  }

  /**
   * merges the main picture and the component pictures to create a mosaic
   */
  void merge() {
    int cnt = 0;
    Paint painter = new Paint();
    painter.setXfermode(settings.getPainterType());
    for (int j = 0; j < newWidth &&
        !((PictureProcessingActivity) activity).processingTask.isCancelled(); j += tileSize) {
      for (int i = 0; i < newWidth; i += tileSize) {
        Colour currentAverage = quadrants.get(cnt);

        Picture preferredPicture = pictureList.get
            (minDifferenceIndex(pictureAverages, currentAverage));

        painter.setShader(new BitmapShader(preferredPicture.getBitmap(), Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT));
        canvas.drawRect(i, j, i + tileSize, j + tileSize, painter);

        cnt++;
      }
    }
  }

  /**
   * Concurrently calculates the average for each section of the picture
   *
   * @return a list of averages for each quadrant (section) of the main picture
   */
  @SuppressWarnings("unchecked")
  private List<Colour> getQuadrantsConcurrently() {
    int[] threadBounds = initialiseQuadrantThreadBounds();
    List<Colour>[] arrayList = (List<Colour>[]) new List[NUMBER_OF_THREADS];
    Thread[] threads = initialiseQuadrantThreads(threadBounds, arrayList);

    for (Thread t : threads) {
      t.run();
    }

    for (Thread thread : threads) {
      thread.start();
    }

    computeQuadrants(threadBounds[NUMBER_OF_THREADS - 1], threadBounds[NUMBER_OF_THREADS],
        NUMBER_OF_THREADS - 1, arrayList);

    try {
      for (Thread thread : threads) {
        thread.join();
      }
    } catch (InterruptedException e) {
      //e.printStackTrace();
    }

    List<Colour> quadrants = new ArrayList<>();
    for (List l : arrayList) {
      quadrants.addAll(l);
    }

    return quadrants;
  }

  /**
   * assigns as many threads as there are cores, including main thread
   * @param threadBounds array containing the bounds for each thread
   * @param listArray array of the lists containing the averages
   * @return array of all threads
   */
  private Thread[] initialiseQuadrantThreads(final int[] threadBounds,
                                             final List<Colour>[] listArray) {
    Thread[] threads = new Thread[NUMBER_OF_THREADS - 1];
    for (int i = 0; i < threads.length; i++) {
      final int finalI = i;
      Runnable r = new Runnable() {
        @Override
        public void run() {
          computeQuadrants(threadBounds[finalI], threadBounds[finalI + 1], finalI, listArray);
        }
      };
      threads[i] = new Thread(r);
    }
    return threads;
  }


  /**
   * assigns the bounds of the image on which each individual thread will operate
   * @return an array with the subsequent bounds
   */
  private int[] initialiseQuadrantThreadBounds() {
    int[] threadBounds = new int[NUMBER_OF_THREADS + 1];
    for (int i = 0; i < NUMBER_OF_THREADS; i++) {
      threadBounds[i] = oldHeight / NUMBER_OF_THREADS * i / tileSize *
          tileSize;
    }
    threadBounds[threadBounds.length - 1] = oldHeight;

    return threadBounds;
  }

  /**
   * calculates the average for the section of the original picture running from lowerHeight to
   * upperHeight
   * @param lowerHeight lower bound of the section
   * @param upperHeight upper bound of the section
   * @param threadNumber corresponding thread
   * @param listArray    corresponding list array where the averages are stored
   */
  private void computeQuadrants(int lowerHeight, int upperHeight, int threadNumber,
                                List<Colour>[] listArray) {
    int tileStep = tileSize / scalar;
    listArray[threadNumber] = new ArrayList<>();
    for (int j = lowerHeight; j < upperHeight; j += tileStep) {
      for (int i = 0; i < oldWidth; i += tileStep) {
        int bottom = j + tileStep - 1 >= upperHeight ?
            upperHeight - 1 : j + tileStep - 1;
        int right = i + tileStep - 1 >= oldWidth ?
            oldWidth - 1 : i + tileStep - 1;

        listArray[threadNumber].add(Colour.getAverage(new Coordinate(i, j), new Coordinate
            (right, bottom), originalPicture, Settings.QUADRANTS_STEP));
      }
    }

  }

  /**
   * computes the smallest difference in average colour
   *
   * @param averages : list containing all the colour averages of each component picture
   * @param colour   : average colour of the current quadrant (section) of the main picture
   * @return index representing the position in the list of component pictures where the picture
   * with the closest average to the quadrant is found
   */
  int minDifferenceIndex(List<Colour> averages, Colour colour) {
    int red = colour.getRed();
    int blue = colour.getBlue();
    int green = colour.getGreen();

    int minDifference = Integer.MAX_VALUE;
    int minIndex = -1;
    int cnt = 0;

    for (Colour c : averages) {
      int difference = Math.abs(c.getBlue() - blue) + Math
          .abs(c.getGreen() - green) + Math
          .abs(c.getRed() - red);
      if (difference < minDifference) {
        minDifference = difference;
        minIndex = cnt;
      }
      cnt++;
    }

    return minIndex;
  }

  /**
   * resolves the type of painter to be used to blend the component pictures and the main picture
   */
  private void resolvePainterType() {
    int red = 0;
    int blue = 0;
    int green = 0;
    for (int i = 0; i < quadrants.size(); i++) {
      red += quadrants.get(i).getRed();
      green += quadrants.get(i).getGreen();
      blue += quadrants.get(i).getBlue();
    }

    red /= quadrants.size();
    green /= quadrants.size();
    blue /= quadrants.size();

    if ((red + green + blue) / 3 >= 128) {
      //System.out.println("MULTIPLY");
      settings.setPainterType(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    } else {
      //System.out.println("OVERLAY");
      settings.setPainterType(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
    }
  }

  /**
   * recycles the picture, instructing the garbage collector to free up memory
   */
  public void recyclePicture() {
    originalPicture = null;
    resizedPicture = null;
    canvas = null;
    System.gc();
  }
}
