package picture;

import android.graphics.BitmapShader;
import android.graphics.Paint;
import android.graphics.Shader;
import android.support.v7.app.AppCompatActivity;


import picsels.PictureProcessingActivity;


public class ConcurrentProcess extends Process {
  private int sectionThreadBounds[];
  private Thread threads[];

  public ConcurrentProcess(Settings settings, AppCompatActivity activity) {
    super(settings, activity);

    initialiseSectionThreadBounds();
    initialiseThreads();
  }

  /**
   * assigns the bounds of the image on which each individual thread will operate
   */
  private void initialiseSectionThreadBounds() {
    sectionThreadBounds = new int[NUMBER_OF_THREADS + 1];
    for (int i = 0; i < NUMBER_OF_THREADS; i++) {
      sectionThreadBounds[i] = newHeight / NUMBER_OF_THREADS * i / tileSize *
          tileSize;
    }
    sectionThreadBounds[sectionThreadBounds.length - 1] = newHeight;
  }

  /**
   * assigns as many threads as there are cores, including main thread
   */
  private void initialiseThreads() {
    threads = new Thread[NUMBER_OF_THREADS - 1];
    for (int i = 0; i < threads.length; i++) {
      final int finalI = i;
      Runnable r = new Runnable() {
        @Override
        public void run() {
          mergeConcurrently(finalI);
        }
      };
      threads[i] = new Thread(r);
    }
  }

  @Override
  void merge() {
    for (Thread thread : threads) {
      thread.start();
    }

    mergeConcurrently(NUMBER_OF_THREADS - 1);

    try {
      for (Thread thread : threads) {
        thread.join();
      }
    } catch (InterruptedException e) {
      //e.printStackTrace();
    }
  }

  /**
   * merges the section of the main picture associate with threadNumber
   * and the component pictures to create a section of a mosaic
   * @param threadNumber section of the picture where the thread will work
   */
  private void mergeConcurrently(int threadNumber) {
    int lowerHeight = sectionThreadBounds[threadNumber];
    int upperHeight = sectionThreadBounds[threadNumber + 1];
    Paint painter = new Paint();
    painter.setXfermode(settings.getPainterType());

    for (int j = lowerHeight; j < upperHeight &&
        !((PictureProcessingActivity) activity).processingTask.isCancelled(); j += tileSize) {
      for (int i = 0; i < newWidth; i += tileSize) {
        int position = j / tileSize
            * ((newWidth - 1) / tileSize + 1) + i / tileSize;
        Colour currentAverage = quadrants.get(position);

        Picture preferredPicture = pictureList.get
            (minDifferenceIndex(pictureAverages, currentAverage));

        painter.setShader(new BitmapShader(preferredPicture.getBitmap(), Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT));
        canvas.drawRect(i, j, i + tileSize, j + tileSize, painter);
      }
    }
  }
}
