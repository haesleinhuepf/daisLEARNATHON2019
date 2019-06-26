/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package de.mpicbg.imagej;

import ij.IJ;
import ij.ImagePlus;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 *
 */
@Plugin(type = Command.class, menuPath = "Plugins>Gauss Filtering")
public class TiledView<T extends RealType<T>> implements Command {

    //@Parameter (style = "directory")
    private File inputfolder = new File("C:/structure/data/daisdata/");

    @Override
    public void run() {

        CLIJ clij = CLIJ.getInstance();

        ClearCLBuffer inputStack = null;
        ClearCLBuffer downsampled = null;
        ClearCLBuffer slice = null;
        ClearCLBuffer tileMap = null;
        ClearCLBuffer tileMapStack = null;
        ClearCLBuffer resultStack = null;

        float downsampleXYFactor = 0.25f;

        int tilesX = 4;
        int tilesY = 2;

        int numberOfImages = inputfolder.listFiles().length;

        int tiles = tilesX * tilesY;

        int imageCount = 0;
        for (File file : inputfolder.listFiles()) {
            if (file.getName().endsWith(".tif")) {

                // load data to GPU
                ImagePlus imp = IJ.openImage(file.toString());
                inputStack = clij.push(imp);

                // in case temp / result images haven't been setup yet
                if (downsampled == null) {
                    downsampled = clij.create(new long[]{
                            (long)(inputStack.getWidth() * downsampleXYFactor),
                            (long)(inputStack.getHeight() * downsampleXYFactor),
                            inputStack.getDepth()},
                            inputStack.getNativeType());

                    slice = clij.create(new long[]{
                            downsampled.getWidth(),
                            downsampled.getHeight()},
                            downsampled.getNativeType());

                    tileMap = clij.create(new long[]{
                                    slice.getWidth() * tilesX,
                                    slice.getHeight() * tilesY},
                            slice.getNativeType());

                    tileMapStack = clij.create(new long[]{
                                    slice.getWidth() * tilesX,
                                    slice.getHeight() * tilesY,
                                    tiles},
                            slice.getNativeType());

                    resultStack = clij.create(new long[]{
                                    slice.getWidth() * tilesX,
                                    slice.getHeight() * tilesY,
                                    numberOfImages},
                            slice.getNativeType());
                }

                // downsample input
                clij.op().downsample(inputStack, downsampled, downsampleXYFactor, downsampleXYFactor, 1.0f);

                int tilePosX = 0;
                int tilePosY = 0;
                int tileCount = 0;
                for (int z = 0; z < downsampled.getDepth(); z += (downsampled.getDepth() / tiles)) {
                    // copy a single slice
                    clij.op().copySlice(downsampled, slice, z);
                    //clij.show(slice, "slice");

                    // translate this slice at a tile position
                    AffineTransform3D at = new AffineTransform3D();
                    at.translate(tilePosX * slice.getWidth(), tilePosY * slice.getHeight() , 0 );
                    clij.op().affineTransform(slice, tileMap, at);
                    //clij.show(tileMap,"tileMap");

                    // copy the tile in a stack to collect all tiles
                    clij.op().copySlice(tileMap, tileMapStack, tileCount);
                    //clij.show(tileMapStack,"tileMapStack");

                    tileCount++;
                    tilePosX ++;
                    if (tilePosX >= tilesX) {
                        tilePosX = 0;
                        tilePosY ++;
                    }
                    //break;
                }

                // draw a maximum projection of the tile stack to get a single image with tiles
                clij.op().maximumZProjection(tileMapStack, tileMap);

                // save the tiles in a stack representing frames
                clij.op().copySlice(tileMap, resultStack, imageCount);
                imageCount++;
                //break;
            }
        }
        clij.show(resultStack, "resultstack");
        //clij.pull(resultStack).show();

        inputStack.close();
        downsampled.close();
        slice.close();
        tileMap.close();
        tileMapStack.close();
        resultStack.close();
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // invoke the plugin
        ij.command().run(TiledView.class, true);

    }

}






































