/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package de.mpicbg.imagej;

import ij.process.AutoThresholder;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.img.Img;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.roi.mask.integer.RandomAccessibleAsMask;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.DoubleColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Plugin(type = Command.class, menuPath = "Plugins>Gauss Filtering")
public class CellCountingWorkflow<T extends RealType<T>> implements Command {

    @Parameter
    private Dataset currentData;

    @Parameter
    ImageJ ij;

    @Override
    public void run() {
        final Img<T> image = (Img<T>)currentData.getImgPlus();

        CLIJ clij = CLIJ.getInstance();

        ClearCLBuffer gpuInput = clij.push(image);
        ClearCLBuffer gpuBlurred = clij.create(gpuInput);
        ClearCLBuffer gpuThresholded = clij.create(gpuBlurred);

        float sigma = 3;
        clij.op().blur(gpuInput, gpuBlurred, sigma, sigma);

        clij.show(gpuBlurred, "blurred");

        clij.op().automaticThreshold(gpuBlurred, gpuThresholded, AutoThresholder.Method.Otsu.name());

        clij.show(gpuThresholded, "thresholded");


        IterableInterval thresholded = Views.iterable(clij.pullBinaryRAI(gpuThresholded));

        gpuInput.close();
        gpuBlurred.close();
        gpuThresholded.close();

        //if (true) return;

        //RandomAccessibleInterval blurred = ij.op().filter().gauss(image, 3, 3);

        //ij.ui().show(blurred);

        //IterableInterval ii = (IterableInterval)blurred;

        //IterableInterval thresholded = ij.op().threshold().otsu(ii);

        ij.ui().show(thresholded);

        invertImage(thresholded);

        ij.ui().show(thresholded);

        RandomAccessibleInterval thresholdedRai = ij.convert().convert(thresholded, RandomAccessibleInterval.class);

        ImgLabeling labeling = ij.op().labeling().cca(thresholdedRai, ConnectedComponents.StructuringElement.FOUR_CONNECTED);

        RandomAccessibleInterval indexImage = labeling.getIndexImg();
        ij.ui().show(indexImage);

        LabelRegions<IntegerType> regions = new LabelRegions(labeling);

        DoubleColumn areaColumn = new DoubleColumn();
        DoubleColumn intensityColumn = new DoubleColumn();

        for(LabelRegion region : regions) {
            IterableInterval sample = Regions.sample(region, image);

            RealType meanIntensity = ij.op().stats().mean(sample);

            double area = region.size();
            double mean = meanIntensity.getRealDouble();

            areaColumn.add(area);
            intensityColumn.add(mean);
        }

        Table table = new DefaultGenericTable();
        table.add(areaColumn);
        table.add(intensityColumn);

        ij.ui().show(table);


        System.out.println("Hello world!");
    }

    private void invertImage(IterableInterval thresholded) {
        Cursor cursor = thresholded.cursor();

        while (cursor.hasNext()) {
            BitType pixel = (BitType)cursor.next();
            pixel.set(!pixel.get());
        }

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

        // ask the user for a file to open
        final File file = new File("C:/structure/data/blobs.tif");
                // ij.ui().chooseFile(null, "open");

        if (file != null) {
            // load the dataset
            final Dataset dataset = ij.scifio().datasetIO().open(file.getPath());

            // show the image
            ij.ui().show(dataset);

            // invoke the plugin
            ij.command().run(CellCountingWorkflow.class, true);
        }
    }

}






































